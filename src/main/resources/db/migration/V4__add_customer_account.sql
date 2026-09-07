-- V4__add_customer_account.sql

-- --------------------------------------------------------
-- Table: customer_account
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_account (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (username)
);

-- --------------------------------------------------------
-- Table: reservation_owner
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation_owner (
    reservation_id UUID PRIMARY KEY REFERENCES reservation(id) ON DELETE CASCADE,
    customer_account_id UUID NOT NULL REFERENCES customer_account(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reservation_owner_customer_account_id ON reservation_owner(customer_account_id);

COMMENT ON TABLE reservation_owner IS 'Binds a reservation to its CustomerAccount owner for per-object authorization.';
