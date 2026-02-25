ALTER TABLE rooms ADD COLUMN state_updated_at TIMESTAMP;
UPDATE rooms SET state_updated_at = NOW() WHERE state_updated_at IS NULL;
