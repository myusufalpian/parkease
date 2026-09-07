-- V11__add_extension_charge.sql
-- Adds a separate additional charge for reservation extension.
-- The base invoice (V7) remains immutable; extension never mutates it.

CREATE TABLE IF NOT EXISTS extension_charge (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    additional_duration_minutes BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_extension_charge_reservation_id ON extension_charge(reservation_id);

COMMENT ON TABLE extension_charge IS 'Separate additional charge for reservation extension. base invoice stays immutable.';
