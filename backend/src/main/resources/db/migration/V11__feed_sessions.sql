-- Frozen rankings keep pagination stable while engagement changes.
-- Each page still evaluates the live post ACL for the session owner.
CREATE TABLE feed_sessions (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 scope VARCHAR(255) NOT NULL,
 post_ids JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW()+INTERVAL '30 minutes'
);
CREATE INDEX idx_feed_sessions_expiry ON feed_sessions(expires_at);
CREATE INDEX idx_feed_sessions_user ON feed_sessions(user_id,created_at DESC);
