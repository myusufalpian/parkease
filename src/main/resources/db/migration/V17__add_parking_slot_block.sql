CREATE TABLE IF NOT EXISTS parking_slot_block (
    id UUID PRIMARY KEY,
    slot_id UUID NOT NULL REFERENCES parking_slot(id) ON DELETE CASCADE,
    blocked_start TIMESTAMP WITH TIME ZONE NOT NULL,
    blocked_end TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CHECK (blocked_start < blocked_end),
    CHECK (char_length(reason) <= 255)
);

CREATE INDEX IF NOT EXISTS idx_parking_slot_block_slot_id ON parking_slot_block(slot_id);
CREATE INDEX IF NOT EXISTS idx_parking_slot_block_status ON parking_slot_block(status);

ALTER TABLE parking_slot_block
    ADD CONSTRAINT no_overlapping_blocks
    EXCLUDE USING GIST (
        slot_id WITH =,
        tstzrange(blocked_start, blocked_end, '[)') WITH &&
    )
    WHERE (status = 'ACTIVE');

COMMENT ON TABLE parking_slot_block IS 'Time-bounded operational blocks for individual parking slots; half-open interval [blocked_start, blocked_end), only ACTIVE rows participate in conflict checks.';
