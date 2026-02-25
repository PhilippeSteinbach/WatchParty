-- Initial schema for WatchParty Phase 1

CREATE TABLE rooms (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(8)      NOT NULL UNIQUE,
    name            VARCHAR(100)    NOT NULL,
    control_mode    VARCHAR(20)     NOT NULL,
    host_connection_id  VARCHAR(255),
    current_video_url   VARCHAR(2048),
    current_time_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_playing      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP WITH TIME ZONE
);

CREATE TABLE participants (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname        VARCHAR(255)    NOT NULL,
    connection_id   VARCHAR(255)    NOT NULL,
    is_host         BOOLEAN         NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    room_id         UUID            NOT NULL REFERENCES rooms(id) ON DELETE CASCADE
);

CREATE INDEX idx_rooms_code ON rooms(code);
CREATE INDEX idx_participants_room_id ON participants(room_id);
CREATE INDEX idx_participants_connection_id ON participants(connection_id);
