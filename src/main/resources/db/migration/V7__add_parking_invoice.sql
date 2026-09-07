-- V7__add_parking_invoice.sql
-- Adds the base invoice created and paid at booking time.
-- One reservation has at most one base invoice.

CREATE TABLE IF NOT EXISTS parking_invoice (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    duration_minutes BIGINT NOT NULL,
    subtotal DECIMAL(19, 2) NOT NULL,
    discount_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    total DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PAYMENT_PENDING',
    pricing_snapshot JSONB NOT NULL DEFAULT '{}',
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (reservation_id)
);

COMMENT ON TABLE parking_invoice IS 'Base invoice created and paid at booking from the planned reservation interval.';
