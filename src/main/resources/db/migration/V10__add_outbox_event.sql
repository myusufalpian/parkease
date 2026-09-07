-- V10__add_outbox_event.sql
-- Adds the durable outbox for payment/refund side effects.
-- External payment provider calls are never made inside the same
-- transaction as reservation/invoice/refund state changes.

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_status ON outbox_event(status);

COMMENT ON TABLE outbox_event IS 'Durable outbox for payment/refund adapter dispatch.';
