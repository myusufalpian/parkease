# ParkEase

Parking reservation platform: reserve parking slots across multiple lots with database-enforced double-booking prevention, a full reservation lifecycle (create → check-in → check-out → cancel/extend), planned-time prepayment billing, and custom JWT authentication with per-reservation ownership.

## Features

- **Authentication** — register/login issue a short-lived HS256 access token and a long-lived refresh token. Refresh rotates the token (single-use) and revokes the previous session; logout revokes the session. Each session records the parsed device (browser/OS family) and the raw client IP.
- **Authorization** — reservation endpoints require a valid access token; per-object access is enforced so only the reservation owner (or an operator) can act on a reservation or read its invoice.
- **Create reservation (book)** — deterministic slot selection (lowest floor, then slot id); overlapping windows for the same slot are rejected with HTTP 409, enforced by a PostgreSQL exclusion constraint. Booking resolves a rate card, computes the prepayment invoice, and binds the reservation to the caller's account. The planned window is bounded (default 31 days).
- **Check-in** — activates a pending reservation and marks the slot occupied; check-in later than the 30-minute grace becomes `NO_SHOW` and releases the slot. Requires a paid invoice.
- **Check-out** — completes the reservation, releases the slot, and publishes a checkout event. Idempotent: retrying on a completed reservation returns the existing result without side effects. Blocked while an extension charge is unpaid.
- **Cancel** — releases the slot; idempotent on terminal state; sets a late-cancellation flag when an active reservation is cancelled within the grace window. Applies the cancellation-fee / refund split and records the acting account.
- **Extend** — updates the planned end time (conflict-checked against the same slot) and raises a separate additional charge that must be paid before the extended window is usable.
- **Availability** — lists available slots for a lot and time window (no authentication required).
- **Parking inventory** — Flyway V18 seeds two canonical lots, eight deterministic slots, and baseline CAR/MOTORCYCLE rate cards. Public APIs expose lots and slots before availability is queried.
- **Slot blocking** — operators and admins can create, list, and cancel time-bounded blocks for individual slots. Active blocks participate in availability and booking conflict checks.
- **Booking windows** — booking start cannot be in the past; the minimum duration defaults to 30 minutes, the future horizon defaults to 90 days, and the maximum reservation duration remains 31 days.
- **Public API protection** — lot, slot, and availability reads are rate-limited to 60 requests per minute per operation and client IP in the single-instance deployment target.
- **Billing engine** — planned-time prepayment with continuous 30-minute rounding; invoices, refunds, and payment references are persisted. Payment is captured through a `PaymentAdapter` port; an outbox dispatcher publishes payment/refund events and a reconciler expires unpaid invoices past a timeout. State transitions are guarded so a capture and a timeout cannot both win.
- **Promotions** — eligible promotions are validated against lot, vehicle, customer type, and effective period. Usage is held at booking, consumed after successful check-in, and released when the reservation is cancelled or expires.
- **Demand pricing** — active demand rules adjust the rate card from occupancy for the requested lot, vehicle type, and time window. The result is stored in the invoice pricing snapshot.
- **Authentication hardening** — disabled accounts cannot use existing access tokens, and login/register requests are rate-limited for the single-instance deployment target.

### Not implemented

- Redis advisory cache wiring, distributed rate limiting, multi-region deployment, mobile clients, and admin dashboard.
- A production payment provider: the bundled `PaymentAdapter` is an in-process implementation restricted to non-production profiles. In the `prod` profile a real adapter must be wired or startup fails closed (see `architecture.md`).

## API contract (`/api/v1`)

Errors return `{ timestamp, status, code, message }` with no stack traces or internal detail. Reservation, extend, cancel, check-in/out, invoice, and admin block endpoints require a `Bearer` access token; authentication, lot discovery, slot discovery, and availability are public.

| Method | Path | Auth | Success | Errors |
|---|---|---|---|---|
| `POST` | `/auth/register` | public | 201 | 400 |
| `POST` | `/auth/login` | public | 200 | 400, 401 |
| `POST` | `/auth/refresh` | public | 200 | 400, 401 |
| `POST` | `/auth/logout` | public | 200 | 400, 401 |
| `GET` | `/lots` | public | 200 | 429 |
| `GET` | `/lots/{lotId}` | public | 200 | 404, 429 |
| `GET` | `/lots/{lotId}/slots` | public | 200 | 404, 429 |
| `POST` | `/admin/slots/{slotId}/blocks` | operator/admin | 201 | 400, 401, 403, 404, 409, 422 |
| `GET` | `/admin/slots/{slotId}/blocks` | operator/admin | 200 | 401, 403, 404 |
| `DELETE` | `/admin/slot-blocks/{blockId}` | operator/admin | 200 | 401, 403, 404 |
| `POST` | `/reservations` | required | 201 | 400, 401, 404, 409, 422 |
| `POST` | `/reservations/{reservationId}/check-in` | required (owner/operator) | 200 | 401, 403, 404, 409 |
| `POST` | `/reservations/{reservationId}/check-out` | required (owner/operator) | 200 | 401, 403, 404, 409, 422 |
| `DELETE` | `/reservations/{reservationId}` | required (owner/operator) | 200 | 401, 403, 404, 409, 422 |
| `PUT` | `/reservations/{reservationId}/extend` | required (owner/operator) | 200 | 401, 403, 404, 409, 422 |
| `GET` | `/invoices/{reservationId}` | required (owner/operator) | 200 | 401, 403, 404 |
| `GET` | `/lots/{lotId}/availability?start=&end=&vehicleType=` | public | 200 | 400, 404, 422 |

**Auth requests**: register/login `{ username (3–100), password (8–72 bytes) }`; refresh/logout `{ refreshToken (≤256) }`. **Auth response**: `{ accessToken, refreshToken, accessTokenExpiresInSeconds }`. Device information is parsed from the `User-Agent` header (capped at 500 chars); client IP uses the servlet remote address.

**Request (`POST /reservations`)**: `{ lotId (UUID), vehicleType (≤50), plate (≤20), plannedStart, plannedEnd }` (ISO offset date-time; end after start; window within the maximum duration). Returns a `BookingResponse` carrying the reservation and its invoice.

**Reservation response**: `{ id, status, slot{ id, slotId, vehicleType, floor }, plate, plannedStart, plannedEnd, actualStart, actualEnd, cancellationReason, lateCancellation }`.

**Extend request**: `{ plannedEnd }` → returns the extension charge. **Cancel request** (optional body): `{ reason (≤100) }` → returns the cancellation/refund result.

**Error codes**: `INVALID_REQUEST` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404), `CONFLICT` (409), `BUSINESS_VALIDATION_FAILED` (422), `TOO_MANY_REQUESTS` (429), and `INTERNAL_ERROR` (500).

### Endpoint details

All request and response bodies use JSON. Date-time values use ISO-8601 offset date-time; identifiers use UUID unless stated otherwise. Protected routes require `Authorization: Bearer <accessToken>`.

#### Authentication

| Endpoint | Request body | Response |
|---|---|---|
| `POST /api/v1/auth/register` | `{ "username": "driver1", "password": "strong-password" }` | `201` with `AuthResponse` |
| `POST /api/v1/auth/login` | `{ "username": "driver1", "password": "strong-password" }` | `200` with `AuthResponse` |
| `POST /api/v1/auth/refresh` | `{ "refreshToken": "..." }` | `200` with rotated `AuthResponse` |
| `POST /api/v1/auth/logout` | `{ "refreshToken": "..." }` | `200` with an empty body |

`AuthResponse` is `{ accessToken, refreshToken, accessTokenExpiresInSeconds }`. Username length is 3–100 characters, password length is 8–72 UTF-8 bytes, and refresh tokens are limited to 256 characters. Login and registration are rate-limited per client IP for the single-instance deployment target.

#### Reservations and billing

| Endpoint | Request | Response |
|---|---|---|
| `POST /api/v1/reservations` | `ReservationRequest` | `201 BookingResponse` |
| `POST /api/v1/reservations/{reservationId}/check-in` | no body | `200 ReservationResponse` |
| `POST /api/v1/reservations/{reservationId}/check-out` | no body | `200 ReservationResponse` |
| `DELETE /api/v1/reservations/{reservationId}` | optional `{ "reason": "..." }` | `200 CancellationResponse` |
| `PUT /api/v1/reservations/{reservationId}/extend` | `{ "plannedEnd": "2026-09-08T12:00:00Z" }` | `200 ExtensionChargeResponse` |
| `GET /api/v1/invoices/{reservationId}` | no body | `200 InvoiceResponse` |

`ReservationRequest` is `{ lotId, vehicleType, plate, plannedStart, plannedEnd, promoCode? }`. `vehicleType` is limited to 50 characters, `plate` to 20 characters, and `promoCode` to 50 characters; `plannedEnd` must be after `plannedStart`.

`ReservationResponse` contains `{ id, status, slot, plate, plannedStart, plannedEnd, actualStart, actualEnd, cancellationReason, lateCancellation }`. The nested `slot` contains `{ id, slotId, vehicleType, floor }`.

`BookingResponse` contains `{ reservation: ReservationResponse, invoice: InvoiceResponse }`. `InvoiceResponse` contains `{ id, reservationId, durationMinutes, subtotal, discountAmount, total, currency, paymentStatus, generatedAt, paidAt }`.

`CancellationResponse` contains `{ reservation, refundStatus, cancellationFee, refundAmount }`. `ExtensionChargeResponse` contains `{ reservation, additionalDurationMinutes, amount, currency }`.

Reservation mutations and invoice reads require the reservation owner, operator, or administrator role. Booking creates the invoice and payment workflow atomically; check-in requires a paid invoice; check-out is idempotent; extension requires its additional charge to be paid before the extended period is usable.

#### Availability

`GET /api/v1/lots/{lotId}/availability?start={isoDateTime}&end={isoDateTime}&vehicleType={vehicleType}` is public. The response is `AvailabilityResponse`: `{ lotId, plannedStart, plannedEnd, slots }`, where each slot is `{ id, slotId, vehicleType, floor }`. `vehicleType` is optional.

Availability excludes slots in `MAINTENANCE`, slots with an overlapping `PENDING`/`ACTIVE` reservation, and slots with an overlapping `ACTIVE` block. Intervals use `[start, end)`, so a slot ending at 12:00 can be used by another interval starting at 12:00. `RESERVED` and `OCCUPIED` are lifecycle states and are not global filters for non-overlapping future windows.

#### Parking inventory

Flyway V18 provides the canonical local/integration inventory:

| Lot ID | Name | Slots |
|---|---|---:|
| `11111111-1111-1111-1111-111111111111` | ParkEase Central | 4 |
| `22222222-2222-2222-2222-222222222222` | ParkEase South | 4 |

`GET /api/v1/lots` returns `LotResponse`: `{ id, name, location, timezone }`. `GET /api/v1/lots/{lotId}/slots` returns `SlotResponse`: `{ id, slotId, vehicleType, floor, status }`. Lot results are ordered by UUID; slot results are ordered by floor and slot ID. Unknown lot IDs return `404 RESOURCE_NOT_FOUND`.

#### Slot blocks

Slot blocks are operational intervals, not customer reservations. Only `OPERATOR` and `ADMIN` may access these endpoints:

| Endpoint | Request | Response |
|---|---|---|
| `POST /api/v1/admin/slots/{slotId}/blocks` | `{ blockedStart, blockedEnd, reason }` | `201 BlockResponse` |
| `GET /api/v1/admin/slots/{slotId}/blocks` | no body | `200 BlockResponse[]` |
| `DELETE /api/v1/admin/slot-blocks/{blockId}` | no body | `200 BlockResponse` |

`blockedStart` must be before `blockedEnd`; `reason` is required and limited to 255 characters. Block states are `ACTIVE`, `CANCELLED`, and `EXPIRED`. Expiry is refreshed during read/delete operations. An active reservation or active block overlap returns `409 CONFLICT`.

#### Booking-window configuration

The effective defaults are configured in `application.yaml` and can be overridden through environment variables:

| Property | Environment variable | Default |
|---|---|---:|
| `parkease.booking.minimum-duration-minutes` | `PARKEASE_BOOKING_MINIMUM_DURATION_MINUTES` | `30` |
| `parkease.booking.future-horizon-days` | `PARKEASE_BOOKING_FUTURE_HORIZON_DAYS` | `90` |

Booking validation returns `422 BUSINESS_VALIDATION_FAILED` when the start is in the past, the duration is below the minimum, the start exceeds the future horizon, the end is not after the start, or the interval exceeds 31 days.

### cURL examples

Set `{{PARKEASE_BASE_URL}}` to `http://localhost:8085`. Replace the other variables with values from the response or the canonical seed. Every JSON request uses `Content-Type: application/json`; protected requests use `Authorization: Bearer {{ACCESS_TOKEN}}`.

#### Authentication

```bash
curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/auth/login' \
  --header 'Content-Type: application/json' \
  --data '{
  "username": "driver1",
  "password": "strong-password"
}'

curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/auth/refresh' \
  --header 'Content-Type: application/json' \
  --data '{
  "refreshToken": "{{REFRESH_TOKEN}}"
}'
```

Login returns `200 OK`; refresh returns a rotated `AuthResponse`. Register uses the same request format as login and returns `201 Created`. Logout uses the refresh token and returns `200 OK` with an empty body.

#### Discover lots and slots

```bash
curl --request GET \
  --url '{{PARKEASE_BASE_URL}}/api/v1/lots'

curl --request GET \
  --url '{{PARKEASE_BASE_URL}}/api/v1/lots/{{LOT_ID}}/slots'
```

#### Check availability

```bash
curl --request GET \
  --url '{{PARKEASE_BASE_URL}}/api/v1/lots/{{LOT_ID}}/availability?start=2026-09-09T10%3A00%3A00Z&end=2026-09-09T12%3A00%3A00Z&vehicleType=CAR'
```

#### Create a slot block

```bash
curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/admin/slots/{{SLOT_ID}}/blocks' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}' \
  --header 'Content-Type: application/json' \
  --data '{
  "blockedStart": "2026-09-09T08:00:00Z",
  "blockedEnd": "2026-09-09T10:00:00Z",
  "reason": "cleaning"
}'
```

#### List and cancel slot blocks

```bash
curl --request GET \
  --url '{{PARKEASE_BASE_URL}}/api/v1/admin/slots/{{SLOT_ID}}/blocks' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}'

curl --request DELETE \
  --url '{{PARKEASE_BASE_URL}}/api/v1/admin/slot-blocks/{{BLOCK_ID}}' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}'
```

#### Create a reservation with the booking-window rules

```bash
curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/reservations' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}' \
  --header 'Content-Type: application/json' \
  --data '{
  "lotId": "{{LOT_ID}}",
  "vehicleType": "CAR",
  "plate": "B1234XYZ",
  "plannedStart": "2026-09-09T10:00:00Z",
  "plannedEnd": "2026-09-09T12:00:00Z",
  "promoCode": "WELCOME10"
}'
```

#### Reservation lifecycle

```bash
curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/reservations/{{RESERVATION_ID}}/check-in' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}'

curl --request POST \
  --url '{{PARKEASE_BASE_URL}}/api/v1/reservations/{{RESERVATION_ID}}/check-out' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}'

curl --request DELETE \
  --url '{{PARKEASE_BASE_URL}}/api/v1/reservations/{{RESERVATION_ID}}' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}' \
  --header 'Content-Type: application/json' \
  --data '{"reason":"schedule changed"}'

curl --request PUT \
  --url '{{PARKEASE_BASE_URL}}/api/v1/reservations/{{RESERVATION_ID}}/extend' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}' \
  --header 'Content-Type: application/json' \
  --data '{"plannedEnd":"2026-09-09T13:00:00Z"}'

curl --request GET \
  --url '{{PARKEASE_BASE_URL}}/api/v1/invoices/{{RESERVATION_ID}}' \
  --header 'Authorization: Bearer {{ACCESS_TOKEN}}'
```

#### Errors and events

Errors use `{ timestamp, status, code, message }` and never expose stack traces. Validation and malformed JSON return `400`; authentication failures return `401`; ownership failures return `403`; missing resources return `404`; overlap or state conflicts return `409`; business validation failures return `422`; unexpected failures return `500`.

`ReservationCheckedOutEvent(reservationId, actualEnd)` is published after a successful check-out and is not republished for an idempotent retry.

## Technology

| Component | Version / Note |
|---|---|
| Java | 25 (LTS) toolchain |
| Spring Boot | 4.1.1 (Spring Framework 7, Jakarta EE 11, Hibernate ORM 7) |
| Build | Gradle 9.7.1 (wrapper) |
| Database | PostgreSQL (Flyway migrations, `btree_gist`) |
| Cache | Redis (available in Docker Compose; not authoritative) |
| Auth | Custom JWT via `io.jsonwebtoken:jjwt` 0.13.0 (HS256); BCrypt via `spring-security-crypto` |
| Device parsing | `com.github.ua-parser:uap-java` 1.6.1 |
| JSON | Jackson databind + `jackson-datatype-jsr310` (JSR-310) |
| Test | JUnit 5, H2 (unit tier), Testcontainers PostgreSQL (integration tier) |

## Module map

```
domain/       JPA entities + state transitions (Reservation, ParkingSlot, ParkingLot, ParkingSlotBlock, PricingPromotion,
              PromotionHold, DemandPricingRule, CustomerAccount, Session, ReservationOwner, RateCard,
              ParkingInvoice, BillingOperation,
              Refund, OutboxEvent, ExtensionCharge, AuditRecord)
repository/   Spring Data JPA repositories
service/      ReservationService (lifecycle); InventoryConflictService, BookingWindowValidator,
              SlotBlockService; AuthService + AuthorizationService; billing services
              (BillingReservationService, BillingCancellationService, BillingExtensionService),
              BillingCalculator, RateCardResolver, CancellationFeeCalculator; PaymentAdapter port +
              InProcessPaymentAdapter; OutboxDispatcher, PaymentTimeoutReconciler; AuditService
controller/   ReservationController, AuthController, BillingController, LotController,
              AdminSlotBlockController (REST)
security/     JwtTokenService, JwtAuthenticationFilter, RefreshTokenGenerator, DeviceInfoParser,
              AuthenticatedPrincipal, AuthRateLimiter, PublicApiRateLimiter
exception/    ApiException hierarchy + ApiExceptionHandler (@RestControllerAdvice)
mapper/       ReservationMapper, PricingSnapshotMapper, InvoiceMapper, PaymentReferenceSnapshotMapper
dto/          request/response records
event/        ReservationCheckedOutEvent
config/       TimeConfiguration (Clock), SecurityConfiguration, BillingConfiguration,
              JwtProperties, BillingProperties, BookingProperties, BookingWindowConfiguration,
              PaymentAdapterGuardConfiguration
```

Request/data flow: controller (`@Valid` DTO, principal resolved from the JWT filter) → service (`@Transactional`) → repositories / mappers. Overlap prevention is enforced at the database tier; the service catches the exclusion-constraint violation (SQLSTATE `23P01`) and maps it to HTTP 409; under heavy concurrency the same guard may surface as a deadlock (`40P01`). Dependency direction: controller → service → repository/domain; mapping only in `mapper/`.

## Database

Flyway migrations `V1`–`V18` (PostgreSQL, `btree_gist`):

- Reservation core: `parking_lot`, `parking_slot`, `reservation`, `pricing_promotion` (`V1`); overlap-guard exclusion constraint (`V2`); `parking_slot.version` optimistic lock (`V3`).
- Overlap guard (`V2`): `EXCLUDE USING GIST (slot_id WITH =, tstzrange(planned_start, planned_end, '[)') WITH &&) WHERE (status IN ('PENDING','ACTIVE'))` — touching endpoints allowed; `CANCELLED`/`COMPLETED`/`NO_SHOW` history never blocks new bookings.
- Auth: `customer_account` + `reservation_owner` (`V4`), `session` (`V5`).
- Billing: `rate_card` (`V6`), `parking_invoice` (`V7`), `billing_operation` (`V8`), `refund` (`V9`), `outbox_event` (`V10`), `extension_charge` (`V11`, payment status in `V14`), billing integrity constraints (`V12`), `audit_record` (`V13`), rate-card immutability trigger (`V12`, extended in `V15`).
- Promotions: `promotion_hold` (`V16`) tracks held, consumed, and released promotion usage.
- Slot blocking: `parking_slot_block` (`V17`) stores half-open operational block intervals, with `ACTIVE`, `CANCELLED`, and `EXPIRED` states plus an active-block exclusion constraint.
- Canonical inventory: `V18` seeds two lots, eight deterministic slots, and baseline CAR/MOTORCYCLE rate cards using idempotent UUID inserts.
- Demand pricing: `demand_pricing_rule` stores active occupancy thresholds and multipliers.

## Setup / run / test / build

- Unit tests (H2 tier): `./gradlew test`
- Coverage: `./gradlew test jacocoTestReport` → `build/reports/jacoco/test/html/index.html`
- PostgreSQL integration tests (exclusion constraint, JSONB round-trip, version invariant, multi-instance concurrency, lifecycle, outbox): `./gradlew postgresIntegrationTest` — **requires Docker**; skipped automatically when Docker is unavailable.
- Docker Compose: copy `.env.example` to `.env`, set a strong `PARKEASE_JWT_SECRET`, then run `docker compose up --build`. The setup runs one app instance with PostgreSQL and Redis readiness checks.
- Runtime image: `Dockerfile` uses a multi-stage build, Spring Boot layers, `jdeps`, and `jlink`; the final container runs as a non-root user with a read-only filesystem.

Configuration requires a JWT signing secret (see `JwtProperties`) and billing settings (see `BillingProperties`); the test configuration supplies deterministic values.

## Conventions

- Constructor injection; `@Transactional` at the service layer.
- No wildcard imports or queries; explicit fields.
- Input validation at the boundary (Bean Validation on DTOs).
- Errors via the `ApiException` hierarchy + `@RestControllerAdvice`; no internal detail returned to clients.
- Tests prefer real collaborators / hand-written fakes and deterministic time; no argument matchers or captors.

## Security

- Custom JWT (HS256) access tokens with refresh-token rotation backed by a `session` table; refresh tokens are stored as SHA-256 hashes. Passwords are BCrypt-hashed and bounded to 72 bytes.
- Per-object authorization: reservation and invoice access is checked against the reservation owner.
- Login failures return a uniform response (no account enumeration on login).
- Login and registration are limited to 10 requests per minute per client IP and operation for the single-instance deployment target.
- Forwarded proxy headers are not trusted by default; configure a trusted proxy explicitly before changing this behavior.
- The in-process payment adapter is disabled in the `prod` profile; production must wire a real adapter or startup fails closed.
- Persistence is parameterized (no injection); reservations store a vehicle plate (PII). Deploy/security gate is documented in `architecture.md`.

## Docs

- Architecture, decisions, trade-offs, deployment/security gate: `architecture.md`
