# ParkEase Architecture Overview

## Current State

The reservation lifecycle/API, planned-time prepayment billing, payment/refund workflow via an outbox, custom JWT authentication, per-object authorization, rate-card and invoice persistence, promotion holds, demand pricing, lot/slot discovery, time-bounded slot blocks, configurable booking windows, rate limiting, and an append-only audit trail are implemented. Flyway migrations `V1`–`V18` back these on PostgreSQL.

Local test evidence: `./gradlew test --no-daemon --console=plain` passes in the current workspace. The test suite covers booking-window validation, Flyway V17/V18 contracts, lot discovery, slot-block lifecycle/security, reservation-block integration, PostgreSQL exclusion behavior, and existing billing/auth flows. This is test-suite evidence, not production load proof (see Gate conditions).

Deferred: Redis advisory caching, distributed rate limiting, and a production payment provider. The bundled `PaymentAdapter` is an in-process implementation excluded from the `prod` profile; production must wire a real adapter or startup fails closed. The supported Docker deployment target is one application instance.

## Approved Billing Decisions

### Planned-time prepayment billing

The invoice is created from the planned reservation interval:

```text
billableDuration = plannedEnd - plannedStart
```

`actualStart` and `actualEnd` remain lifecycle/audit timestamps. They do not determine the invoice amount.

The 30-minute grace period only controls check-in eligibility:

```text
checkInDeadline = plannedStart + 30 minutes
```

A late check-in becomes `NO_SHOW`, releases the slot, and receives no refund by default.

### Continuous 30-minute rounding

The complete planned interval is rounded once:

```text
blocks = ceil(totalPlannedMinutes / 30)
```

Blocks are anchored at `plannedStart`. They are assigned to the local date of their block start for daily-cap accounting. A block crossing local midnight is counted once, and each local-midnight crossing applies the configured overnight surcharge.

The lot's timezone is authoritative for local dates and midnight boundaries. Persisted timestamps remain canonical UTC/`TIMESTAMP WITH TIME ZONE`.

### Rate-card source and snapshot

PostgreSQL owns immutable, versioned `rate_card` rows. A published version is never edited. Booking resolves the applicable version and stores a pricing snapshot containing rate-card version, rates, cap, surcharge, currency, rounding mode, and lot timezone.

Redis is advisory cache only. It cannot determine the final price.

Demand pricing resolves active lot/vehicle rules from the requested occupancy window and applies the resulting multiplier to the booking-time rate card. PostgreSQL reservation state remains authoritative; the occupancy metric is a pricing input and is persisted in the pricing snapshot.

### Cancellation and refund

A paid reservation cancellation retains 10% and refunds 90%:

```text
cancellationFee = round(totalPaid × 10%, 2)
refundAmount = totalPaid - cancellationFee
```

Cancellation commits the reservation state change and slot release immediately. Refund processing is asynchronous through an outbox and idempotent payment adapter. Invoice history remains immutable; refund data is stored separately.

Payment timeout defaults to 10 minutes. An unpaid expired hold releases the slot.

### Extension

Extension is an additional charge. The original invoice and pricing snapshot remain immutable. The extension creates a separate charge/adjustment using its own pricing decision before the extended time becomes confirmed.

### Promotions

Promotion eligibility is evaluated against code, effective period, lot, vehicle type, and customer type. Usage is atomically claimed at booking and represented by a `PromotionHold` with `HELD → CONSUMED → RELEASED` transitions. Release is conditional and decrements usage only when the hold transition succeeds.

## Security and Ownership

`CustomerAccount` is the reservation owner. Plate is vehicle data, not identity.

The ownership chain is:

```text
principal → CustomerAccount → Reservation → Invoice/Refund
```

The principal is resolved from a custom JWT access token by a request filter; reservation and invoice endpoints require a valid token and check per-object access against the reservation owner (or an operator). Refresh tokens are single-use, rotated, and stored as SHA-256 hashes in the `session` table; passwords are BCrypt-hashed and bounded to 72 bytes. Login failures return a uniform response so accounts cannot be enumerated on login.

Access tokens are checked against the current account status on every protected request, so disabled accounts cannot continue using an unexpired token. Login and registration use an in-process per-IP rate limiter suitable for the single-instance Docker target. Forwarded proxy headers are not trusted by default; a trusted proxy strategy is required before placing the service behind a proxy that rewrites client identity.

## Data Components

Tables (Flyway `V1`–`V18`):

```text
parking_lot, parking_slot, reservation, pricing_promotion, demand_pricing_rule (V1)
customer_account, reservation_owner                          (V4)
session                                                      (V5)
rate_card                                                    (V6)
parking_invoice                                              (V7)
billing_operation                                            (V8)
refund                                                       (V9)
outbox_event                                                 (V10)
extension_charge                                             (V11, payment status V14)
audit_record                                                 (V13)
promotion_hold                                               (V16)
parking_slot_block                                           (V17)
canonical lot/slot/rate-card seed                            (V18)
```

Important constraints (present in the migrations):

```text
EXCLUDE GIST reservation(slot_id, tstzrange(planned_start, planned_end)) WHERE status IN ('PENDING','ACTIVE')  (V2)
UNIQUE parking_invoice(reservation_id)                     (V7)
UNIQUE refund(payment_transaction_id)                      (V9)
UNIQUE billing_operation(idempotency_key)                  (V8)
UNIQUE billing_operation(reservation_id, operation_type)   (V8)
UNIQUE rate_card(lot_id, vehicle_type, version)            (V6)
UNIQUE customer_account(username)                          (V4)
UNIQUE session(refresh_token_hash)                         (V5)
CHECK  non-negative money on rate_card / parking_invoice   (V12)
TRIGGER published rate_card is immutable                   (V12, extended V15)
EXCLUDE GIST parking_slot_block(slot_id, tstzrange(blocked_start, blocked_end)) WHERE status = 'ACTIVE' (V17)
CHECK blocked_start < blocked_end                            (V17)
```

`billing_operation` records durable operation state; it is not a generic lock table. Reservation `owner_id` binding lives in the separate `reservation_owner` join table.

### Inventory and booking windows

Flyway `V18` seeds two canonical lots with deterministic UUIDs, eight parking slots, and active CAR/MOTORCYCLE rate cards. The seed is idempotent by UUID and supplies the baseline inventory used by local and integration environments.

`ParkingSlotBlock` stores an operational block for one slot using a half-open interval `[blocked_start, blocked_end)`. Its states are `ACTIVE`, `CANCELLED`, and `EXPIRED`. Only active blocks participate in conflict checks. Expiration is evaluated at read/write time; correctness does not depend on a scheduler.

Booking windows are validated by `BookingWindowValidator` using the application clock:

```text
plannedStart < plannedEnd
duration >= parkease.booking.minimum-duration-minutes (default 30)
plannedStart >= now
plannedStart <= now + parkease.booking.future-horizon-days (default 90)
duration <= 31 days
```

Availability excludes slots in `MAINTENANCE`, PENDING/ACTIVE reservations overlapping the requested interval, and ACTIVE slot blocks overlapping the requested interval. `RESERVED` and `OCCUPIED` are lifecycle states and are not global time-window filters.

`InventoryConflictService` locks a slot with PostgreSQL `PESSIMISTIC_WRITE` before reservation or block conflict checks. Reservation and block writes then check both conflict sources in the same transaction; the database exclusion constraints remain the final same-table guard.

## Request Flows

### Booking and prepayment

1. Authorize customer and validate request.
2. Begin PostgreSQL transaction.
3. Validate the booking window and select a deterministic slot after locking/checking reservation and block conflicts.
4. Resolve demand pricing, apply an eligible promotion hold, and calculate the planned-time invoice using continuous blocks.
5. Persist reservation, pricing snapshot, invoice `PAYMENT_PENDING`, and payment/outbox operation.
6. Commit the slot hold.
7. Payment adapter captures payment idempotently.
8. On success, mark invoice `PAID`; reservation can check in.
9. On failure or 10-minute timeout, cancel the unpaid hold and release the slot.

### Inventory discovery and slot blocking

- Public lot reads expose `GET /api/v1/lots`, `GET /api/v1/lots/{lotId}`, and `GET /api/v1/lots/{lotId}/slots`; responses are deterministically ordered and unknown lots return `404`.
- Public availability is exposed by `GET /api/v1/lots/{lotId}/availability` and is protected by a per-IP public API rate limiter.
- `OPERATOR` and `ADMIN` users can create, list, and cancel blocks through `/api/v1/admin/**`.
- Block creation locks the slot, rejects reservation/block overlap, persists an ACTIVE block, and records an audit event.
- Block cancellation is idempotent for already terminal blocks; expired ACTIVE blocks become EXPIRED when read or changed.

### Check-in and checkout

- Check-in validates `PENDING` plus invoice `PAID`, records actual check-in time, and changes the slot to `OCCUPIED`.
- Late check-in becomes `NO_SHOW`, releases the slot, and receives no refund by default.
- Checkout performs an atomic `ACTIVE → COMPLETED` transition, releases the slot, and publishes an after-commit/outbox lifecycle event. It does not recalculate the invoice.

### Cancellation and refund

1. Lock or atomically transition the reservation and invoice operation.
2. Validate the paid state and idempotency key.
3. Calculate 10% fee and 90% refund.
4. Mark reservation cancelled and slot available in one transaction.
5. Create refund record and outbox command.
6. Process and reconcile provider result asynchronously.

## Consistency and Idempotency

- PostgreSQL exclusion constraint remains the source of truth for overlapping reservation windows.
- PostgreSQL exclusion constraint on `parking_slot_block` remains the source of truth for overlapping ACTIVE blocks.
- Reservation/block cross-conflicts are checked while holding a pessimistic write lock on the slot row.
- Invoice creation is protected by `UNIQUE(reservation_id)`.
- State transitions use conditional updates or row locks and verify affected-row count.
- Booking windows are validated against the injected application clock and configured duration/horizon limits.
- Payment/refund/outbox operations use durable idempotency keys.
- No generic application lock is required for multi-instance correctness.

## API Contracts

- `POST /api/v1/auth/register`, `/login`, `/refresh`, and `/logout` implement account and session lifecycle; login/register are rate-limited for the single-instance target.
- `GET /api/v1/lots`, `GET /api/v1/lots/{lotId}`, and `GET /api/v1/lots/{lotId}/slots` are public inventory discovery endpoints with deterministic ordering.
- `GET /api/v1/lots/{lotId}/availability` is public and returns available slots for a requested time window; public lot and availability reads use an in-process per-IP rate limiter.
- `POST /api/v1/admin/slots/{slotId}/blocks`, `GET /api/v1/admin/slots/{slotId}/blocks`, and `DELETE /api/v1/admin/slot-blocks/{blockId}` require `OPERATOR` or `ADMIN` authentication.
- `POST /api/v1/reservations` returns reservation, invoice, and payment status; eligible promotion and demand pricing decisions are included in the pricing snapshot.
- `POST /api/v1/reservations/{id}/check-out` completes lifecycle only; invoice already exists.
- `DELETE /api/v1/reservations/{id}` returns cancellation fee, refund amount, and refund status.
- `GET /api/v1/invoices/{reservationId}` requires owner/operator authorization.

Handled API errors use `{ timestamp, status, code, message }`. Validation errors use `INVALID_REQUEST` or `BUSINESS_VALIDATION_FAILED`; authentication/role failures use `UNAUTHORIZED`/`FORBIDDEN`; missing resources use `RESOURCE_NOT_FOUND`; inventory overlap and lifecycle conflicts use `CONFLICT`.

## Observability and SLO

These are target metrics, not yet proven. Metrics, traces, and alerts are not wired, and no staging load/soak has validated them; treat them as gate criteria rather than achieved results.

Target metrics:

- availability/create p95 ≤ 300 ms;
- checkout persistence p95 ≤ 500 ms;
- invoice GET p95 ≤ 200 ms;
- cancellation acknowledgement p95 ≤ 300 ms;
- 99.5% initial monthly API availability;
- zero duplicate invoices;
- zero completed reservations without invoices;
- 100% deterministic billing fixture accuracy;
- RPO ≤ 5 minutes and RTO ≤ 30 minutes.

Logs and audit records include correlation ID, actor, reservation, invoice/refund IDs, operation, result, latency, pricing version, and error code. Plate and payment data are masked.

## Options and Decision

### Option A — Transactional database core plus unique constraints and outbox (selected)

Reservation/invoice/refund state is committed transactionally. External payment and audit side effects use an outbox. Unique constraints and operation records provide idempotency.

This is selected for the first billing increment because it is easiest to prove with PostgreSQL and preserves strong consistency without introducing a fully asynchronous invoice experience.

### Option B — Fully asynchronous event-driven billing

Checkout or booking emits events and a separate billing consumer creates invoices eventually. This improves independent scaling but introduces `PAYMENT_PENDING`, `INVOICE_PENDING`, reconciliation, and eventual consistency throughout the customer API. Deferred for now.

### Option C — Generic lock table

Rejected as the primary concurrency mechanism. It introduces stale-lock cleanup and deadlock risks and does not replace database uniqueness. A purpose-built `billing_operation` table is retained only for durable idempotency state.

## Deployment and Recovery

Migrations are additive: customer account, rate card, invoice, payment/refund, operation, outbox, and slot-block tables are introduced before application writes depend on them. V18 supplies deterministic canonical inventory and baseline rate-card data. Invoice and refund data are never destructively rolled back; recovery uses forward fixes, backup/restore, and reconciliation.

Before public exposure:

Satisfied now:

- authentication and per-object authorization are active;
- the PostgreSQL integration tier is green and can be made merge-blocking;
- payment/refund side effects run through the outbox with durable idempotency keys, and invoice/refund data is never destructively rolled back.

Outstanding (gate conditions):

- rate limiting is active for the supported single-instance deployment; distributed rate limiting is required if the topology changes;
- a production payment adapter is wired (the in-process adapter is `prod`-excluded and startup fails closed without a real one);
- registration currently reports duplicate usernames; public deployments should add an anti-enumeration response policy;
- metrics, traces, alerts, and payment/refund failure runbooks are wired and tested;
- published `rate_card` is protected at the role level (revoke UPDATE and trigger management from runtime DB roles so the immutability trigger cannot be disabled);
- CI actions are SHA-pinned;
- staging load/soak validates the SLOs.

## Deployment Boundary

The provided Docker Compose deployment runs one application instance with PostgreSQL and Redis. The application image is multi-stage and uses Spring Boot layers, `jdeps`, and `jlink`; the runtime runs as a non-root user with a read-only filesystem and a writable `/tmp` tmpfs. PostgreSQL is the source of truth, while Redis is provisioned for future advisory caching and is not required for correctness.
