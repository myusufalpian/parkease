-- V1__initial_schema.sql
-- Initialize core database schema for ParkEase
-- PostgreSQL 9.2+ syntax with btree_gist extension

-- Enable btree_gist extension for exclusion constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- --------------------------------------------------------
-- Table: parking_lot
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS parking_lot (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(500),
    timezone VARCHAR(50) NOT NULL,
    operating_hours JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- Table: parking_slot
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS parking_slot (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES parking_lot(id) ON DELETE CASCADE,
    slot_id VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    floor INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- Exclusion constraint will be added separately via ALTER TABLE
    -- to avoid issues if migration re-runs; constraint added in V2
    UNIQUE (lot_id, slot_id)
);

-- --------------------------------------------------------
-- Table: reservation
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES parking_slot(id) ON DELETE CASCADE,
    customer_plate VARCHAR(20),
    planned_start TIMESTAMP WITH TIME ZONE NOT NULL,
    planned_end TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_start TIMESTAMP WITH TIME ZONE,
    actual_end TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cancellation_reason VARCHAR(100),
    late_cancellation BOOLEAN NOT NULL DEFAULT FALSE,
    promo_code VARCHAR(50),
    rate_card_version INTEGER NOT NULL DEFAULT 1,
    pricing_snapshot JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- Table: pricing_promotion
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pricing_promotion (
    code VARCHAR(50) PRIMARY KEY,
    lot_id UUID REFERENCES parking_lot(id) ON DELETE SET NULL,
    vehicle_type VARCHAR(50),
    customer_type VARCHAR(50),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(19, 2) NOT NULL,
    usage_limit INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- --------------------------------------------------------
-- Exclusion constraint documentation
-- --------------------------------------------------------
-- V2 adds no_overlapping_reservations directly to reservation.
-- --------------------------------------------------------
COMMENT ON TABLE reservation IS 'Reservation entity with a slot_id-scoped, half-open tstzrange(planned_start, planned_end, ''[)'') constraint for PENDING and ACTIVE reservations.';

-- Insert sample rate card reference (optional; can be populated via demo data)
-- The actual rate card logic is handled in billing service, not a separate table in Phase 1.