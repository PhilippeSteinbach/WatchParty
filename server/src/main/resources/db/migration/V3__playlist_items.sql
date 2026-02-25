CREATE TABLE playlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    video_url VARCHAR(500) NOT NULL,
    title VARCHAR(300),
    thumbnail_url VARCHAR(500),
    duration_seconds INTEGER DEFAULT 0,
    added_by VARCHAR(50) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    added_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_playlist_items_room_id ON playlist_items(room_id);
