CREATE TABLE IF NOT EXISTS promotion_hold (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservation(id) ON DELETE CASCADE,
    promotion_code VARCHAR(50) NOT NULL REFERENCES pricing_promotion(code),
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_promotion_hold_status ON promotion_hold(status);

CREATE TABLE IF NOT EXISTS demand_pricing_rule (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES parking_lot(id) ON DELETE CASCADE,
    vehicle_type VARCHAR(50),
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_to TIMESTAMP WITH TIME ZONE NOT NULL,
    occupancy_threshold DECIMAL(5, 4) NOT NULL,
    multiplier DECIMAL(8, 4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
ALTER TABLE demand_pricing_rule ADD CONSTRAINT chk_demand_occupancy_threshold CHECK (occupancy_threshold >= 0 AND occupancy_threshold <= 1);
ALTER TABLE demand_pricing_rule ADD CONSTRAINT chk_demand_multiplier_positive CHECK (multiplier > 0);
ALTER TABLE demand_pricing_rule ADD CONSTRAINT chk_demand_effective_window CHECK (effective_to > effective_from);
ALTER TABLE customer_account ADD COLUMN customer_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL';
