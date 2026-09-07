ALTER TABLE reservation
    ADD CONSTRAINT no_overlapping_reservations
    EXCLUDE USING GIST (
        slot_id WITH =,
        tstzrange(planned_start, planned_end, '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'ACTIVE'));

COMMENT ON CONSTRAINT no_overlapping_reservations ON reservation IS
'Prevents overlapping PENDING/ACTIVE reservations for the same slot using half-open interval [start, end). Touching endpoints do not overlap; CANCELLED/COMPLETED/NO_SHOW history never blocks new bookings.';

COMMENT ON TABLE reservation IS 'Reservation entity with exclusion constraint preventing slot double-booking.';
