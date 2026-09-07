-- V8__add_billing_operation.sql
-- Adds the durable idempotency record for billing operations.
-- This is a purpose-built operation-state table, not a generic lock table:
-- it records that an operation already completed so retries return the
-- existing result instead of re-running side effects.

CREATE TABLE IF NOT EXISTS billing_operation (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    operation_type VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    response_snapshot JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (idempotency_key),
    UNIQUE (reservation_id, operation_type)
);

COMMENT ON TABLE billing_operation IS 'Durable idempotency record for checkout/cancellation/payment/refund operations; not a lock table.';
