-- Phase 3: User accounts for authentication (H2 compatible)

CREATE TABLE users (
    id            UUID          DEFAULT gen_random_uuid() PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    display_name  VARCHAR(100)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);
