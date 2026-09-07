-- V13__add_audit_record.sql
-- append-only audit trail for reservation/billing/refund mutations.

CREATE TABLE IF NOT EXISTS audit_record (
    id UUID PRIMARY KEY,
    actor_id UUID,
    action VARCHAR(50) NOT NULL,
    target VARCHAR(100),
    reason VARCHAR(255),
    before_state VARCHAR(255),
    after_state VARCHAR(255),
    correlation_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_record_target ON audit_record(target);
CREATE INDEX IF NOT EXISTS idx_audit_record_occurred_at ON audit_record(occurred_at);

COMMENT ON TABLE audit_record IS 'Append-only audit trail: actor, UTC time, correlation id, reason, before/after.';
