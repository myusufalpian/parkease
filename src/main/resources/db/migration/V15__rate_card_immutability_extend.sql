-- V15__rate_card_immutability_extend.sql
-- Extend published rate-card immutability to the identity/effective-window
-- fields (lot_id, vehicle_type, effective_to) in addition to the monetary
-- fields already frozen in V12. Only the status transition remains mutable
-- (e.g. ACTIVE -> RETIRED).
--
-- Deployment note (not application code): also revoke UPDATE and trigger
-- management on rate_card from the runtime DB roles so a privileged
-- connection cannot disable this trigger.

CREATE OR REPLACE FUNCTION enforce_rate_card_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.hourly_rate IS DISTINCT FROM NEW.hourly_rate
        OR OLD.daily_cap IS DISTINCT FROM NEW.daily_cap
        OR OLD.overnight_surcharge IS DISTINCT FROM NEW.overnight_surcharge
        OR OLD.currency IS DISTINCT FROM NEW.currency
        OR OLD.version IS DISTINCT FROM NEW.version
        OR OLD.effective_from IS DISTINCT FROM NEW.effective_from
        OR OLD.effective_to IS DISTINCT FROM NEW.effective_to
        OR OLD.lot_id IS DISTINCT FROM NEW.lot_id
        OR OLD.vehicle_type IS DISTINCT FROM NEW.vehicle_type THEN
        RAISE EXCEPTION 'published rate_card fields are immutable (id=%)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
