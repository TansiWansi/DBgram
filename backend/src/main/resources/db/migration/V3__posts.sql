-- ═══════════════════════════════════════════════════════════════════
-- V3: Posts (JSONB Blocks), PostVersions, PostAuthors (Multi-Author)
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    community_id    UUID REFERENCES communities(id) ON DELETE SET NULL,

    -- Structured content: array of blocks [{type:"text",data:{...}}, {type:"carousel",data:{...}}]
    blocks          JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Denormalized plaintext for full-text search (extracted from blocks)
    text_content    TEXT,

    -- Post type derived from blocks
    post_type       VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (post_type IN ('STANDARD', 'REEL', 'STORY', 'ARTICLE')),

    -- Visibility / ACL
    visibility      VARCHAR(30) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'FOLLOWERS_ONLY', 'CLOSE_FRIENDS', 'COMMUNITY_ONLY', 'CUSTOM', 'PRIVATE')),

    -- Media provenance
    media_hash      VARCHAR(64),           -- SHA-256 hash
    merkle_root     VARCHAR(64),           -- Merkle tree root if batched
    blockchain_tx   VARCHAR(128),          -- L2 transaction hash
    provenance_anchored BOOLEAN NOT NULL DEFAULT FALSE,

    -- Engagement counters (denormalized, updated via events)
    like_count      BIGINT NOT NULL DEFAULT 0,
    comment_count   BIGINT NOT NULL DEFAULT 0,
    share_count     BIGINT NOT NULL DEFAULT 0,
    save_count      BIGINT NOT NULL DEFAULT 0,
    view_count      BIGINT NOT NULL DEFAULT 0,

    -- Settings
    comments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_pinned       BOOLEAN NOT NULL DEFAULT FALSE,

    -- Hashtags (extracted from blocks for indexing)
    hashtags        TEXT[] DEFAULT '{}',

    -- Version tracking
    current_version INTEGER NOT NULL DEFAULT 1,

    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REMOVED', 'UNDER_REVIEW')),

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_posts_community     ON posts (community_id) WHERE community_id IS NOT NULL;
CREATE INDEX idx_posts_visibility    ON posts (visibility);
CREATE INDEX idx_posts_status        ON posts (status);
CREATE INDEX idx_posts_type          ON posts (post_type);
CREATE INDEX idx_posts_created       ON posts (created_at DESC);
CREATE INDEX idx_posts_published     ON posts (published_at DESC) WHERE status = 'PUBLISHED';
CREATE INDEX idx_posts_blocks        ON posts USING GIN (blocks);
CREATE INDEX idx_posts_hashtags      ON posts USING GIN (hashtags);
CREATE INDEX idx_posts_media_hash    ON posts (media_hash) WHERE media_hash IS NOT NULL;
CREATE INDEX idx_posts_text_search   ON posts USING GIN (to_tsvector('english', COALESCE(text_content, '')));

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Post Authors (Multi-Author with Contribution Percentage) ──
CREATE TABLE post_authors (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id           UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role              VARCHAR(20) NOT NULL DEFAULT 'AUTHOR'
        CHECK (role IN ('AUTHOR', 'CO_AUTHOR', 'CONTRIBUTOR', 'EDITOR')),
    contribution_pct  SMALLINT NOT NULL DEFAULT 100
        CHECK (contribution_pct >= 0 AND contribution_pct <= 100),
    added_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, user_id)
);

CREATE INDEX idx_post_authors_post ON post_authors (post_id);
CREATE INDEX idx_post_authors_user ON post_authors (user_id);

-- Constraint: contribution_pct for all authors of a post must sum to 100
-- (enforced at application level for flexibility, with periodic DB audit)

-- ── Post Versions (Immutable Edit History) ──
CREATE TABLE post_versions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    version_number  INTEGER NOT NULL,
    blocks          JSONB NOT NULL,
    text_content    TEXT,
    edited_by       UUID NOT NULL REFERENCES users(id),
    edit_summary    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, version_number)
);

CREATE INDEX idx_post_versions_post ON post_versions (post_id, version_number DESC);

-- ── Comments (threaded) ──
CREATE TABLE comments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES comments(id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    like_count      BIGINT NOT NULL DEFAULT 0,
    is_pinned       BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
        CHECK (status IN ('VISIBLE', 'HIDDEN', 'REMOVED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post       ON comments (post_id, created_at DESC);
CREATE INDEX idx_comments_author     ON comments (author_id);
CREATE INDEX idx_comments_parent     ON comments (parent_id) WHERE parent_id IS NOT NULL;

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
