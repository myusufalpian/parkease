-- V12__billing_integrity_constraints.sql
-- Enforce non-negative monetary values and rate-card immutability
-- at the database boundary, not only in documentation.

ALTER TABLE rate_card
    ADD CONSTRAINT chk_rate_card_hourly_rate_non_negative CHECK (hourly_rate >= 0),
    ADD CONSTRAINT chk_rate_card_daily_cap_non_negative CHECK (daily_cap >= 0),
    ADD CONSTRAINT chk_rate_card_overnight_surcharge_non_negative CHECK (overnight_surcharge >= 0);

ALTER TABLE parking_invoice
    ADD CONSTRAINT chk_parking_invoice_subtotal_non_negative CHECK (subtotal >= 0),
    ADD CONSTRAINT chk_parking_invoice_discount_non_negative CHECK (discount_amount >= 0),
    ADD CONSTRAINT chk_parking_invoice_total_non_negative CHECK (total >= 0),
    ADD CONSTRAINT chk_parking_invoice_duration_non_negative CHECK (duration_minutes >= 0);

ALTER TABLE refund
    ADD CONSTRAINT chk_refund_fee_non_negative CHECK (fee_amount >= 0),
    ADD CONSTRAINT chk_refund_amount_non_negative CHECK (refund_amount >= 0);

-- Rate-card immutability: a published (ACTIVE) row's monetary and effective
-- fields must never change; only the status transition to RETIRED is allowed.
CREATE OR REPLACE FUNCTION enforce_rate_card_immutability()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.hourly_rate IS DISTINCT FROM NEW.hourly_rate
        OR OLD.daily_cap IS DISTINCT FROM NEW.daily_cap
        OR OLD.overnight_surcharge IS DISTINCT FROM NEW.overnight_surcharge
        OR OLD.currency IS DISTINCT FROM NEW.currency
        OR OLD.version IS DISTINCT FROM NEW.version
        OR OLD.effective_from IS DISTINCT FROM NEW.effective_from THEN
        RAISE EXCEPTION 'published rate_card fields are immutable (id=%)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rate_card_immutability
    BEFORE UPDATE ON rate_card
    FOR EACH ROW
    EXECUTE FUNCTION enforce_rate_card_immutability();
