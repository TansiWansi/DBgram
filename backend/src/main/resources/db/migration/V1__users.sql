-- ═══════════════════════════════════════════════════════════════════
-- V1: Users, Profiles, Authentication, Follow Graph
-- ═══════════════════════════════════════════════════════════════════

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ──
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username        VARCHAR(30)  NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20)  UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,

    -- Profile (JSONB for flexible schema evolution)
    display_name    VARCHAR(100) NOT NULL,
    bio             TEXT,
    avatar_url      VARCHAR(512),
    cover_url       VARCHAR(512),
    profile_meta    JSONB DEFAULT '{}'::jsonb,

    -- Verification & Trust
    is_verified     BOOLEAN   NOT NULL DEFAULT FALSE,
    is_business     BOOLEAN   NOT NULL DEFAULT FALSE,
    verification_type VARCHAR(20) DEFAULT 'NONE'
        CHECK (verification_type IN ('NONE', 'BLUE', 'GOLD', 'GOVERNMENT')),

    -- 2FA
    two_fa_enabled  BOOLEAN   NOT NULL DEFAULT FALSE,
    two_fa_secret   VARCHAR(255),

    -- Denormalized counters (updated via triggers/events)
    follower_count  BIGINT    NOT NULL DEFAULT 0,
    following_count BIGINT    NOT NULL DEFAULT 0,
    post_count      BIGINT    NOT NULL DEFAULT 0,

    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'BANNED')),

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX idx_users_username      ON users (username);
CREATE INDEX idx_users_email         ON users (email);
CREATE INDEX idx_users_display_name  ON users (display_name);
CREATE INDEX idx_users_status        ON users (status);
CREATE INDEX idx_users_created_at    ON users (created_at DESC);
CREATE INDEX idx_users_profile_meta  ON users USING GIN (profile_meta);

-- ── Follow Graph ──
CREATE TABLE follows (
    follower_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_close_friend BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id)
);

CREATE INDEX idx_follows_following   ON follows (following_id);
CREATE INDEX idx_follows_close_friend ON follows (follower_id) WHERE is_close_friend = TRUE;

-- ── Blocked Users ──
CREATE TABLE blocked_users (
    blocker_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

-- ── Refresh Tokens ──
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    device_info     VARCHAR(255),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_exp  ON refresh_tokens (expires_at);

-- ── Auto-update updated_at trigger ──
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
