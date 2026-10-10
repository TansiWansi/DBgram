-- ═══════════════════════════════════════════════════════════════════
-- V4: Access Control Lists (Fine-Grained ACL System)
-- ═══════════════════════════════════════════════════════════════════

-- ── Custom ACL Lists (user-defined groups like "Close Friends") ──
CREATE TABLE acl_lists (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    list_type       VARCHAR(20) NOT NULL DEFAULT 'CUSTOM'
        CHECK (list_type IN ('CLOSE_FRIENDS', 'CUSTOM', 'BLOCK_LIST')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, name)
);

CREATE INDEX idx_acl_lists_owner ON acl_lists (owner_id);

-- ── ACL List Members ──
CREATE TABLE acl_list_members (
    list_id         UUID NOT NULL REFERENCES acl_lists(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (list_id, user_id)
);

CREATE INDEX idx_acl_members_user ON acl_list_members (user_id);

-- ── Post ACL Entries ──
-- Links posts to specific ACL rules for CUSTOM visibility
CREATE TABLE post_acl (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,

    -- Rule type determines how to evaluate access
    rule_type       VARCHAR(30) NOT NULL
        CHECK (rule_type IN (
            'ALLOW_USER',           -- specific user allowed
            'DENY_USER',            -- specific user blocked
            'ALLOW_LIST',           -- entire ACL list allowed
            'ALLOW_COMMUNITY',      -- community members allowed
            'ALLOW_TIER'            -- paid tier members allowed
        )),

    -- Exactly one of these should be set based on rule_type
    target_user_id      UUID REFERENCES users(id) ON DELETE CASCADE,
    target_list_id      UUID REFERENCES acl_lists(id) ON DELETE CASCADE,
    target_community_id UUID REFERENCES communities(id) ON DELETE CASCADE,
    target_tier_id      UUID REFERENCES community_tiers(id) ON DELETE CASCADE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════
-- COVERING INDEXES for <10ms ACL Lookups
-- These are the critical indexes that make access control fast.
-- ═══════════════════════════════════════════════════════════════════

-- Fast lookup: "Does this post have any ACL rules?"
CREATE INDEX idx_post_acl_post ON post_acl (post_id);

-- Fast lookup: "Is this user directly allowed/denied?"
CREATE INDEX idx_post_acl_user ON post_acl (post_id, target_user_id)
    WHERE target_user_id IS NOT NULL;

-- Fast lookup: "Is this user in an allowed list?"
CREATE INDEX idx_post_acl_list ON post_acl (post_id, target_list_id)
    WHERE target_list_id IS NOT NULL;

-- Fast lookup: "Is this user in the allowed community?"
CREATE INDEX idx_post_acl_community ON post_acl (post_id, target_community_id)
    WHERE target_community_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════
-- Optimized ACL Check Function (used by application layer)
-- Returns TRUE if user_id can view post_id
-- ═══════════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION check_post_access(
    p_post_id UUID,
    p_user_id UUID
) RETURNS BOOLEAN AS $$
DECLARE
    v_visibility VARCHAR(30);
    v_author_id UUID;
    v_community_id UUID;
BEGIN
    -- 1. Get post metadata
    SELECT p.visibility, pa.user_id, p.community_id
    INTO v_visibility, v_author_id, v_community_id
    FROM posts p
    JOIN post_authors pa ON pa.post_id = p.id
    WHERE p.id = p_post_id
    LIMIT 1;

    -- Author can always see their own post
    IF v_author_id = p_user_id THEN RETURN TRUE; END IF;

    -- 2. Check by visibility type
    CASE v_visibility
        WHEN 'PUBLIC' THEN RETURN TRUE;

        WHEN 'FOLLOWERS_ONLY' THEN
            RETURN EXISTS (
                SELECT 1 FROM follows
                WHERE follower_id = p_user_id AND following_id = v_author_id
            );

        WHEN 'CLOSE_FRIENDS' THEN
            RETURN EXISTS (
                SELECT 1 FROM follows
                WHERE follower_id = v_author_id
                  AND following_id = p_user_id
                  AND is_close_friend = TRUE
            );

        WHEN 'COMMUNITY_ONLY' THEN
            RETURN EXISTS (
                SELECT 1 FROM community_members
                WHERE community_id = v_community_id AND user_id = p_user_id
            );

        WHEN 'CUSTOM' THEN
            -- Check explicit deny first
            IF EXISTS (
                SELECT 1 FROM post_acl
                WHERE post_id = p_post_id
                  AND rule_type = 'DENY_USER'
                  AND target_user_id = p_user_id
            ) THEN RETURN FALSE; END IF;

            -- Check explicit allow
            RETURN EXISTS (
                SELECT 1 FROM post_acl pa
                WHERE pa.post_id = p_post_id AND (
                    (pa.rule_type = 'ALLOW_USER' AND pa.target_user_id = p_user_id)
                    OR
                    (pa.rule_type = 'ALLOW_LIST' AND EXISTS (
                        SELECT 1 FROM acl_list_members alm
                        WHERE alm.list_id = pa.target_list_id AND alm.user_id = p_user_id
                    ))
                    OR
                    (pa.rule_type = 'ALLOW_COMMUNITY' AND EXISTS (
                        SELECT 1 FROM community_members cm
                        WHERE cm.community_id = pa.target_community_id AND cm.user_id = p_user_id
                    ))
                    OR
                    (pa.rule_type = 'ALLOW_TIER' AND EXISTS (
                        SELECT 1 FROM community_members cm
                        WHERE cm.tier_id = pa.target_tier_id
                          AND cm.user_id = p_user_id
                          AND cm.subscription_status = 'ACTIVE'
                    ))
                )
            );

        WHEN 'PRIVATE' THEN RETURN FALSE;

        ELSE RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql STABLE;
