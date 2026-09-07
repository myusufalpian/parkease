-- V6__add_rate_card.sql
-- Adds versioned, immutable-once-published rate cards.
-- A published version is never edited; a price change inserts a new row
-- with an incremented version and a fresh effective_from.

CREATE TABLE IF NOT EXISTS rate_card (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES parking_lot(id) ON DELETE CASCADE,
    vehicle_type VARCHAR(50) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    hourly_rate DECIMAL(19, 2) NOT NULL,
    daily_cap DECIMAL(19, 2) NOT NULL,
    overnight_surcharge DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'IDR',
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (lot_id, vehicle_type, version)
);

CREATE INDEX IF NOT EXISTS idx_rate_card_lot_vehicle_type ON rate_card(lot_id, vehicle_type);

COMMENT ON TABLE rate_card IS 'Versioned, immutable-once-published rate cards; booking snapshots the resolved version.';
