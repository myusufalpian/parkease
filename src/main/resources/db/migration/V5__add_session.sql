-- V5__add_session.sql
-- Adds Session table for refresh-token rotation and session history
-- (issued time, device, IP, revocation) per the approved auth design.

CREATE TABLE IF NOT EXISTS session (
    id UUID PRIMARY KEY,
    customer_account_id UUID NOT NULL REFERENCES customer_account(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(128) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(30),
    user_agent VARCHAR(500),
    device_family VARCHAR(100),
    os_family VARCHAR(100),
    browser_family VARCHAR(100),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (refresh_token_hash)
);

CREATE INDEX IF NOT EXISTS idx_session_customer_account_id ON session(customer_account_id);

COMMENT ON TABLE session IS 'Historical login sessions: refresh-token rotation, device (User-Agent parsed via uap-java), and raw IP address without GeoIP resolution.';
