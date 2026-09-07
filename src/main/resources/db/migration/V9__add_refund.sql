-- V9__add_refund.sql
-- Adds cancellation fee/refund ledger.
-- The base invoice stays immutable, refund is tracked separately.

CREATE TABLE IF NOT EXISTS refund (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES parking_invoice(id) ON DELETE CASCADE,
    payment_transaction_id VARCHAR(100) NOT NULL,
    fee_amount DECIMAL(19, 2) NOT NULL,
    refund_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_reference VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (payment_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_refund_invoice_id ON refund(invoice_id);

COMMENT ON TABLE refund IS 'Cancellation fee (10%) and refund (90%) tracked separately from the immutable base invoice.';
