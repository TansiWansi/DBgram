-- ═══════════════════════════════════════════════════════════════════
-- V2: Communities, Memberships, Paid Tiers, Temporal Events
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE communities (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,
    avatar_url      VARCHAR(512),
    cover_url       VARCHAR(512),
    rules           JSONB DEFAULT '[]'::jsonb,

    -- Type: PERMANENT or TEMPORAL (auto-destruct)
    community_type  VARCHAR(20) NOT NULL DEFAULT 'PERMANENT'
        CHECK (community_type IN ('PERMANENT', 'TEMPORAL')),

    -- For TEMPORAL communities: when to self-destruct
    expires_at      TIMESTAMPTZ,

    -- Visibility
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'HIDDEN')),

    -- Monetization
    is_paid         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Denormalized
    member_count    BIGINT NOT NULL DEFAULT 0,
    post_count      BIGINT NOT NULL DEFAULT 0,

    -- Metadata
    tags            JSONB DEFAULT '[]'::jsonb,
    settings        JSONB DEFAULT '{}'::jsonb,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_communities_slug        ON communities (slug);
CREATE INDEX idx_communities_owner       ON communities (owner_id);
CREATE INDEX idx_communities_type        ON communities (community_type);
CREATE INDEX idx_communities_visibility  ON communities (visibility);
CREATE INDEX idx_communities_expires     ON communities (expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_communities_tags        ON communities USING GIN (tags);

CREATE TRIGGER trg_communities_updated_at
    BEFORE UPDATE ON communities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Community Membership Tiers (for paid communities) ──
CREATE TABLE community_tiers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    community_id    UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    price_cents     INTEGER NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    stripe_price_id VARCHAR(255),
    benefits        JSONB DEFAULT '[]'::jsonb,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_community_tiers_community ON community_tiers (community_id);

-- ── Community Members ──
CREATE TABLE community_members (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    community_id    UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
        CHECK (role IN ('OWNER', 'ADMIN', 'MODERATOR', 'MEMBER')),
    tier_id         UUID REFERENCES community_tiers(id) ON DELETE SET NULL,

    -- For paid memberships
    stripe_subscription_id VARCHAR(255),
    subscription_status    VARCHAR(20) DEFAULT 'NONE'
        CHECK (subscription_status IN ('NONE', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'TRIALING')),
    subscription_expires_at TIMESTAMPTZ,

    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (community_id, user_id)
);

CREATE INDEX idx_cm_community  ON community_members (community_id);
CREATE INDEX idx_cm_user       ON community_members (user_id);
CREATE INDEX idx_cm_role       ON community_members (community_id, role);
CREATE INDEX idx_cm_stripe     ON community_members (stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;

-- ── Community Events (for Google Meet integration) ──
CREATE TABLE community_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    community_id    UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    creator_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    meet_link       VARCHAR(512),
    max_attendees   INTEGER,
    rsvp_count      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'UPCOMING'
        CHECK (status IN ('UPCOMING', 'LIVE', 'ENDED', 'CANCELED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_community ON community_events (community_id);
CREATE INDEX idx_events_starts    ON community_events (starts_at);
CREATE INDEX idx_events_status    ON community_events (status);
