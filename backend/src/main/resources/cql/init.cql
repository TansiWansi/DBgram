-- ═══════════════════════════════════════════════════════════════════
-- DBgram Cassandra Keyspace & Tables
-- Run after Cassandra starts: cqlsh -f init.cql
-- ═══════════════════════════════════════════════════════════════════

-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS dbgram
WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 1
};

USE dbgram;

-- ═══════════════════════════════════════════════════════════════════
-- Messages (E2EE) — Partitioned by conversation, ordered by time
-- The server NEVER sees plaintext; only encrypted blobs.
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS messages_by_conversation (
    convo_id        UUID,
    message_id      TIMEUUID,
    sender_id       UUID,
    -- Encrypted payload (client-side Signal Protocol encryption)
    encrypted_body  BLOB,
    -- Message metadata (NOT encrypted — needed for routing)
    message_type    TEXT,       -- 'TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE', 'REACTION', 'SYSTEM'
    reply_to_id     TIMEUUID,
    -- Ephemeral settings
    is_ephemeral    BOOLEAN,
    ttl_seconds     INT,       -- for disappearing messages
    -- Delivery tracking
    delivered_at    TIMESTAMP,
    read_at         TIMESTAMP,
    -- Soft-delete (20s undo window)
    is_deleted      BOOLEAN,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP,
    PRIMARY KEY (convo_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND default_time_to_live = 0
  AND gc_grace_seconds = 864000
  AND compaction = {'class': 'TimeWindowCompactionStrategy', 'compaction_window_unit': 'DAYS', 'compaction_window_size': 1};

-- ═══════════════════════════════════════════════════════════════════
-- Conversations — User's conversation list
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS conversations_by_user (
    user_id         UUID,
    convo_id        UUID,
    convo_type      TEXT,       -- 'DIRECT', 'GROUP'
    convo_name      TEXT,       -- NULL for direct, name for groups
    participant_ids SET<UUID>,
    is_e2ee         BOOLEAN,
    -- Last message preview (encrypted or system message)
    last_message_preview TEXT,
    last_message_at TIMESTAMP,
    unread_count    INT,
    is_muted        BOOLEAN,
    is_archived     BOOLEAN,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    PRIMARY KEY (user_id, last_message_at, convo_id)
) WITH CLUSTERING ORDER BY (last_message_at DESC, convo_id ASC);

-- ═══════════════════════════════════════════════════════════════════
-- Read Receipts
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS read_receipts (
    convo_id        UUID,
    user_id         UUID,
    last_read_msg   TIMEUUID,
    read_at         TIMESTAMP,
    PRIMARY KEY (convo_id, user_id)
);

-- ═══════════════════════════════════════════════════════════════════
-- Likes — Per-post, with temporal metadata for decay scoring
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS likes_by_post (
    post_id         UUID,
    user_id         UUID,
    like_type       TEXT,       -- 'LIKE', 'LOVE', 'FIRE', 'SAD', 'LAUGH'
    created_at      TIMESTAMP,
    PRIMARY KEY (post_id, user_id)
);

-- Reverse lookup: all posts a user has liked
CREATE TABLE IF NOT EXISTS likes_by_user (
    user_id         UUID,
    post_id         UUID,
    like_type       TEXT,
    created_at      TIMESTAMP,
    PRIMARY KEY (user_id, created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);

-- ═══════════════════════════════════════════════════════════════════
-- Feed — Pre-computed feed entries per user (push model)
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS feed_by_user (
    user_id         UUID,
    score           DOUBLE,     -- Pre-computed decay score
    post_id         UUID,
    author_id       UUID,
    post_type       TEXT,
    -- Denormalized preview data (avoids joins at read-time)
    preview_text    TEXT,
    preview_media   TEXT,       -- first media URL
    author_username TEXT,
    author_avatar   TEXT,
    -- Engagement counts (denormalized snapshot)
    like_count      BIGINT,
    comment_count   BIGINT,
    created_at      TIMESTAMP,
    inserted_at     TIMESTAMP,
    PRIMARY KEY (user_id, score, post_id)
) WITH CLUSTERING ORDER BY (score DESC, post_id ASC)
  AND default_time_to_live = 172800;  -- 48 hours TTL

-- ═══════════════════════════════════════════════════════════════════
-- Notifications — Per user, time-ordered
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS notifications_by_user (
    user_id             UUID,
    notification_id     TIMEUUID,
    notif_type          TEXT,       -- 'LIKE', 'COMMENT', 'FOLLOW', 'MENTION', 'COMMUNITY_INVITE', 'PAYMENT'
    actor_id            UUID,
    actor_username      TEXT,
    actor_avatar        TEXT,
    target_type         TEXT,       -- 'POST', 'COMMENT', 'COMMUNITY', 'USER'
    target_id           UUID,
    preview_text        TEXT,
    is_read             BOOLEAN,
    created_at          TIMESTAMP,
    PRIMARY KEY (user_id, notification_id)
) WITH CLUSTERING ORDER BY (notification_id DESC)
  AND default_time_to_live = 2592000;  -- 30 days TTL

-- ═══════════════════════════════════════════════════════════════════
-- E2EE Key Store — PreKey bundles for Signal Protocol
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS prekey_bundles (
    user_id             UUID,
    device_id           INT,
    identity_key        BLOB,       -- Public identity key
    signed_prekey_id    INT,
    signed_prekey       BLOB,       -- Signed pre-key public
    signed_prekey_sig   BLOB,       -- Signature
    registration_id     INT,
    updated_at          TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);

-- One-Time PreKeys (consumed on use)
CREATE TABLE IF NOT EXISTS one_time_prekeys (
    user_id         UUID,
    device_id       INT,
    prekey_id       INT,
    prekey          BLOB,       -- Public key
    PRIMARY KEY ((user_id, device_id), prekey_id)
);
