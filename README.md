# ParkEase

Parking reservation & billing foundation (Sprint 1): JPA entities, repositories, Flyway migrations with PostgreSQL `EXCLUDE` anti-double-booking constraint.

## Stack

Spring Boot 4.1.1, PostgreSQL + Flyway, Redis (advisory cache, Sprint 2+), H2 for unit tests.

## Migrations

- `V1__initial_schema.sql`: `parking_lot`, `parking_slot`, `reservation`, `pricing_promotion`.
- `V2__add_exclusion_constraint.sql`: `no_overlapping_reservations` on `(slot_id, tsrange '[)')` with `WHERE (status IN ('PENDING','ACTIVE'))` — touching endpoints allowed, terminal history never blocks.

## Run

- Unit tests: `./gradlew test`
- Coverage: `./gradlew test jacocoTestReport` (report: `build/reports/jacoco/test/html/index.html`)
- Postgres exclusion IT (`ReservationExclusionIT`): needs Docker; skipped automatically when Docker is unavailable.

## Docs

- Architecture: `architecture.md`
