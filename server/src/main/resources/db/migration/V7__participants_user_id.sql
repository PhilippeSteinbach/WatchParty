-- Phase 3: Link participants to registered users

ALTER TABLE participants
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_participants_user_id ON participants(user_id);
