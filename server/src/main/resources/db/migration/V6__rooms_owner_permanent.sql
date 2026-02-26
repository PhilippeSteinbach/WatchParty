-- Phase 3: Permanent rooms and owner association

ALTER TABLE rooms
    ADD COLUMN owner_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN is_permanent BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: all existing rooms are anonymous (expires_at already set or null)
-- Anonymous rooms without an expiry get a 24h window from creation
UPDATE rooms
SET expires_at = created_at + INTERVAL '24 hours'
WHERE owner_id IS NULL AND expires_at IS NULL;

CREATE INDEX idx_rooms_owner_id ON rooms(owner_id);
