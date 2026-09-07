# ParkEase: Architecture Overview

## System Overview

ParkEase is a parking reservation and billing platform enabling drivers to reserve parking slots across multiple parking lots with automated usage-based billing. The system handles the complete reservation lifecycle: creation, deterministic slot selection, double-booking prevention, check-in/check-out, and billing with grace periods, daily caps, and promotional discounts.

## Architecture Decision Highlights

### Double-Booking Prevention: PostgreSQL EXCLUDE Constraint + btree_gist

**Decision**: Use PostgreSQL `EXCLUDE` constraint with `btree_gist` extension to prevent overlapping reservations at the database level, supplemented by Redis advisory cache for fast pre-validation.

**Rationale**:
- Moves double-booking prevention from application code to declarative database layer
- Eliminates entire classes of bugs related to missed locking, retry logic, or deadlocks
- Strong consistency guarantee even under concurrent load or across process boundaries
- Redis cache remains advisory only — final truth always in PostgreSQL

**Implementation**:
- Constraint: `ALTER TABLE reservation ADD CONSTRAINT no_overlapping_reservations EXCLUDE USING GIST (slot_id WITH =, tstzrange(planned_start, planned_end, '[)') WITH &&) WHERE (status IN ('PENDING','ACTIVE'));`
- Time range uses half-open interval `[start, end)` so reservation ending exactly when another starts is not overlap
- Terminal history (`CANCELLED`, `COMPLETED`, `NO_SHOW`) never blocks new bookings
- An `EXCLUDE` constraint cannot be handled by `ON CONFLICT` (that clause only matches unique constraints/indexes; pairing it with an exclusion constraint raises `42P10`). An overlap therefore raises SQLSTATE `23P01`, which the service catches and maps to HTTP 409.

### Billing Engine: Usage-Based Calculation with Business Rules

**Design**: Duration = actualEnd - actualStart - gracePeriod (applied once at start), then segmented into calendar-day groups. Each 30-minute segment charged at (hourlyRate / 2). Daily cap per calendar day. Midnight crossings multiplied by overnight surcharge. Promo discounts clamped to subtotal.

**Key Rules**:
- Grace period: applied once at beginning of actual stay, not per calendar day
- 30-minute segments: `ceil(duration_minutes / 30)` segments per day segment
- Daily cap: max charge per calendar day, applied after segment summation
- Midnight crossing: each 00:00 local time transition adds overnight surcharge multiplier
- Promo: subtotal discount clamped — total never negative

### Promotional Discounts: Lifecycle & Quota Management

**Status Flow**: HELD (at booking) → CONSUMED (at successful check-in) → RELEASED (at pre-check-in cancellation)

**Rules**:
- Promo code has scope (lot/vehicle/customer), usage_limit, effective_from/effective_to
- Usage count incremented atomically; rejected if limit exceeded
- HELD → CONSUMED only on check-in success; retry check-in does not reduce quota further
- HELD → RELEASED on cancellation before check-in; quota returned idempotently
- Cancellation after check-in does not auto-release quota (per agreed policy)
- Discount clamped to subtotal — total invoice amount >= 0

### Time Handling: Canonical UTC + Lot Timezone

**Approach**:
- All timestamps stored in canonical UTC in database
- Lot timezone configured as domain field, used for calendar-day billing and midnight crossing
- Grace period and actualStartTime/actualEndUser local time, but snapshot stored in UTC
- Timezone conversion at boundary: UTC → lot ZoneId for calendar operations, lot ZoneId → UTC for persistence

### Concurrency & Fault Tolerance

**Redis as Advisory Cache Only**:
- Availability pre-check via Redis (lot/slot/time-window key with TTL)
- Cache stale results expected — always validate in PostgreSQL transaction
- Redis outage: reservation and billing still work, falling back to PostgreSQL exclusives + exclusion constraint
- Cache invalidation after every lifecycle mutation: create, cancel, check-in, checkout, extend

**Idempotency**:
- Checkout and cancel operations are idempotent: re-running produces same result
- Constraint violations mapped to HTTP 409 with consistent error format
- Reservation status transition checked deterministically on each attempt

### Security & Access Control

**Current state (Sprint 2)**: The reservation API has **no authentication and no authorization/object-ownership checks** yet. This is a deliberate, documented deferral (auth is a Non-Goal for this phase per PRD/RFC), not an oversight. Endpoints mutate shared state and return PII (vehicle plate), so the absence of access control is a real risk **at the deployment boundary**, not in the persistence layer.

**Two complementary controls (both adopted):**

1. **Hard deployment gate (binding now)** — The reservation endpoints MUST NOT be exposed to an untrusted network until authentication + per-object authorization exist. Until then, the service is restricted to a trusted/internal network. This is an operational control (network isolation / bind to internal interface), enforced at deploy time — it does not require application code and is the compensating control that makes deferring auth safe today.

2. **Auth implementation task (scheduled)** — Authentication + per-object (object-ownership) authorization is a **P0 task at the start of Sprint 3, ahead of the billing engine (T18+)**. Rationale: billing generates invoices and touches money/PII; exposing that without authorization is more dangerous than reservation alone, and establishing the identity model first lets billing and promo reuse it. Audit trail (backlog T30) is pulled into this same package so pre/post-auth actions are forensically traceable.

**Design prerequisites (must be decided before auth implementation):**
- **Identity model**: who is the principal? (customer account — which does not yet exist in the domain — vs. lot-operator vs. plate-based). This is an open decision that blocks auth design.
- **Ownership model**: what binds a reservation to its owner for IDOR prevention (account id vs. plate).

**Verified positive controls (already in place)**: parameterized persistence (no SQL injection), no hardcoded production secrets, no unsafe deserialization, JSON error responses with no reflected user input (XSS sink removed), idempotent check-out/cancel (replay-safe), optimistic locking + exclusion constraint (concurrency-safe).

**Not yet in place (edge hardening, bundle with auth before public exposure)**: rate limiting / anti-automation on booking and availability; CI action pinning by commit SHA.

## Trade-Offs Made

| Decision | Alternative | Why Selected |
|---|---|---|
| Exclusion constraint + btree_gist vs Pessimistic locks | `SELECT FOR UPDATE` | Lower contention, declarative guarantee, less custom locking code, simpler reasoning |
| Redis advisory cache vs source of truth | Redis as primary reservation store | Avoids single point of truth complexity; Redis outage doesn't break core flow; simpler deployment |
| Grace period once-before-segmentation vs per-day | Per-day grace application | Agreed business rule; simpler to implement and test; matches consumed promo policy |
| Java 25 (LTS) baseline vs Java 17 floor | Stay on Spring Boot 4's Java 17 floor | Java 25 is GA/LTS with first-class Spring Boot 4 support; adopted deliberately. Java 17 is the framework floor/recommendation, not a requirement. |
| DECIMAL monetary vs BigDecimal in JSON | Float/double | Avoid floating-point precision issues; scale 2 with HALF_UP rounding matches business requirements |

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| **btree_gist extension not available** in target PostgreSQL | Verify PG version (9.2+) before sprint; alternative: custom triggers if unavailable — adds ~2 days effort |
| **Exclusion constraint performance** under high contention | Test with realistic load (Sprint 1-2); half-open intervals `[start,end)` help index usage; constraint uses btree_gist GiST index |
| **Redis cache stale results** after lifecycle mutation | Treat cache as advisory ALWAYS; final validation in DB transaction; TTL as safety net; invalidate after every mutation |
| **Timezone misconfiguration** leading to wrong calendar-day billing | Enforce lot timezone as required domain field; convert at boundary only; unit tests with known timezone offsets |
| **Constraint violation error handling** not mapped to HTTP 409 | Map exclusion-violation SQLSTATE `23P01` in the service (cause-chain walk) to a consistent ErrorResponse DTO with code, message, timestamp. (`23514` is `check_violation`, a different class — the overlap guard raises `23P01`.) |

## Operational Observability

**Key Metrics to Monitor**:
- Constraint violation attempts per day (indicates application logic bugs)
- Reservation creation latency p95 (target: <200ms non-concurrent)
- Concurrent booking success rate (target: 100% with exclusion constraint)
- Redis cache hit/miss ratio for availability checks
- Billing calculation error rate (target: 0% via fixture tests)
- Promo quota exhaustion rate

**Logging Recommendations**:
- Log every exclusion constraint violation (constraint name, lot_id, slot_id, time range attempt)
- Log promo lifecycle transitions (HELD→CONSUMED, HELD→RELEASED) with reservation ID and user/correlation ID
- Log billing calculation inputs/outputs for audit trail (rate card version, demand metric, promo snapshot)
- Log Redis cache misses for availability checks (helps tune cache TTL)

## Deployment Checklist

### Pre-Deployment
- [ ] PostgreSQL version >= 9.2 with `btree_gist` extension enabled
- [ ] Flyway migrations V1 (schema), V2 (exclusion constraint), and V3 (parking_slot.version optimistic-lock column) applied to target DB
- [ ] Redis reachable and configured with appropriate TTL for cache keys
- [ ] Target environment has lot timezone configured via `parkease.lot.timezone` or equivalent

### Post-Deployment Validation
- [ ] Run concurrent booking test: 20 parallel attempts same slot/time → exactly 1 success
- [ ] Test grace period edge case: check-in exactly at 30min boundary → ACTIVE, not NO_SHOW
- [ ] Test billing fixture: verify the defined billing cases match expected totals
- [ ] Test promo lifecycle: HELD→CONSUMED→RELEASED with idempotent quota
- [ ] Verify Java toolchain: `java -version` shows 25.x (LTS baseline)

### Security Gate (blocking before untrusted-network exposure)
- [ ] Authentication + per-object authorization implemented (Sprint 3 P0, ahead of billing), OR
- [ ] Service confirmed restricted to a trusted/internal network (network isolation enforced and documented) if auth is not yet present
- [ ] Rate limiting on booking + availability in place before public exposure
- [ ] CI actions pinned by commit SHA

### Rollback Ready
- [ ] Previous schema version (V1 without exclusion constraint) deployable
- [ ] Previous app version (without constraint-aware code) can read data; writes may create overlaps resolvable manually
- [ ] 24-hour monitoring window after enabling writes before considering rollback window closed