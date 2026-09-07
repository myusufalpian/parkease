# ParkEase

Parking reservation platform: reserve parking slots across multiple lots with database-enforced double-booking prevention and a full reservation lifecycle (create → check-in → check-out → cancel/extend).

## Features

- **Create reservation** — deterministic slot selection (lowest floor, then slot id); overlapping windows for the same slot are rejected with HTTP 409, enforced by a PostgreSQL exclusion constraint.
- **Check-in** — activates a pending reservation and marks the slot occupied; check-in later than the 30-minute grace becomes `NO_SHOW` and releases the slot.
- **Check-out** — completes the reservation, releases the slot, and publishes a checkout event. Idempotent: retrying on a completed reservation returns the existing result without side effects.
- **Cancel** — releases the slot; idempotent on terminal state; sets a late-cancellation flag when an active reservation is cancelled within the grace window.
- **Extend** — updates the planned end time, conflict-checked against the same slot.
- **Availability** — lists available slots for a lot and time window.

### Not implemented

- Authentication / authorization. The API returns no identity checks and performs no object-ownership enforcement; keep it on a trusted/internal network (see `architecture.md`).
- Billing engine, promotions lifecycle, Redis advisory cache wiring, rate limiting, payment, multi-region, mobile clients, admin dashboard.

## API contract (`/api/v1`)

No authentication. Errors return `{ timestamp, status, code, message }` with no stack traces or internal detail.

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/reservations` | 201 | 400, 404, 409, 422 |
| `POST` | `/reservations/{reservationId}/check-in` | 200 | 404, 409 |
| `POST` | `/reservations/{reservationId}/check-out` | 200 | 404, 409, 422 |
| `DELETE` | `/reservations/{reservationId}` | 200 | 404, 409, 422 |
| `PUT` | `/reservations/{reservationId}/extend` | 200 | 404, 409, 422 |
| `GET` | `/lots/{lotId}/availability?start=&end=&vehicleType=` | 200 | 400, 404, 422 |

**Request (`POST /reservations`)**: `{ lotId (UUID), vehicleType (≤50), plate (≤20), plannedStart, plannedEnd }` (ISO offset date-time; end after start).

**Reservation response**: `{ id, status, slot{ id, slotId, vehicleType, floor }, plate, plannedStart, plannedEnd, actualStart, actualEnd, cancellationReason, lateCancellation }`.

**Availability response**: `{ lotId, plannedStart, plannedEnd, slots[]{ id, slotId, vehicleType, floor } }`.

**Extend request**: `{ plannedEnd }`. **Cancel request** (optional body): `{ reason (≤100) }`.

**Error codes**: `INVALID_REQUEST` (400), `RESOURCE_NOT_FOUND` (404), `CONFLICT` (409), `BUSINESS_VALIDATION_FAILED` (422), `INTERNAL_ERROR` (500).

**Event**: `ReservationCheckedOutEvent(reservationId, actualEnd)` published on successful check-out (exactly once; not re-published on checkout retry).

## Technology

| Component | Version / Note |
|---|---|
| Java | 25 (LTS) toolchain |
| Spring Boot | 4.1.1 (Spring Framework 7, Jakarta EE 11, Hibernate ORM 7) |
| Build | Gradle 9.7.1 (wrapper) |
| Database | PostgreSQL (Flyway migrations, `btree_gist`) |
| Cache | Redis (advisory only; not yet wired) |
| Test | JUnit 5, H2 (unit tier), Testcontainers PostgreSQL (integration tier) |

## Module map

```
domain/       JPA entities + state transitions (Reservation, ParkingSlot, ParkingLot, PricingPromotion)
repository/   Spring Data JPA repositories
service/      ReservationService (lifecycle, @Transactional)
controller/   ReservationController (REST)
exception/    ApiException hierarchy + ApiExceptionHandler (@RestControllerAdvice)
mapper/       ReservationMapper, PricingSnapshotMapper
dto/          request/response records
event/        ReservationCheckedOutEvent
config/       TimeConfiguration (Clock bean)
```

Request/data flow: controller (`@Valid` DTO) → `ReservationService` (`@Transactional`) → repositories / `ReservationMapper`. Overlap prevention is enforced at the database tier; the service catches the exclusion-constraint violation (SQLSTATE `23P01`) and maps it to HTTP 409. Dependency direction: controller → service → repository/domain; mapping only in `mapper/`.

## Database

- `parking_lot`, `parking_slot`, `reservation`, `pricing_promotion` (Flyway `V1`).
- Overlap guard (`V2`): `EXCLUDE USING GIST (slot_id WITH =, tstzrange(planned_start, planned_end, '[)') WITH &&) WHERE (status IN ('PENDING','ACTIVE'))` — touching endpoints allowed; `CANCELLED`/`COMPLETED`/`NO_SHOW` history never blocks new bookings.
- `parking_slot.version` optimistic-lock column (`V3`).

## Setup / run / test / build

- Unit tests (H2 tier): `./gradlew test`
- Coverage: `./gradlew test jacocoTestReport` → `build/reports/jacoco/test/html/index.html`
- PostgreSQL integration tests (exclusion constraint, JSONB round-trip, version invariant, multi-instance concurrency, lifecycle): `./gradlew postgresIntegrationTest` — **requires Docker**; skipped automatically when Docker is unavailable.

## Conventions

- Constructor injection; `@Transactional` at the service layer.
- No wildcard imports or queries; explicit fields.
- Input validation at the boundary (Bean Validation on DTOs).
- Errors via the `ApiException` hierarchy + `@RestControllerAdvice`; no internal detail returned to clients.
- Tests prefer real collaborators / hand-written fakes and deterministic time; no argument matchers or captors.

## Security

No authentication or authorization. Do not expose the API to an untrusted network (deploy gate documented in `architecture.md`). Persistence is parameterized (no injection); reservations store a vehicle plate (PII).

## Docs

- Architecture, decisions, trade-offs, deployment/security gate: `architecture.md`
