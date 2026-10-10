ALTER TABLE users ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

-- Only accepted relationships belong in follows. Pending requests grant no access.
CREATE TABLE follow_requests (
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id <> following_id)
);
CREATE INDEX idx_follow_requests_incoming ON follow_requests(following_id, created_at DESC);

CREATE TABLE story_views (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, viewer_id)
);
CREATE INDEX idx_active_stories ON posts(published_at DESC) WHERE post_type='STORY' AND status='PUBLISHED';

CREATE OR REPLACE FUNCTION can_view_account_content(account_id UUID, viewer_id UUID)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM users u
        WHERE u.id=account_id AND u.status='ACTIVE'
          AND (u.id=viewer_id OR NOT u.is_private OR EXISTS (
              SELECT 1 FROM follows f WHERE f.following_id=u.id AND f.follower_id=viewer_id
          ))
          AND NOT EXISTS (
              SELECT 1 FROM blocked_users b
              WHERE (b.blocker_id=u.id AND b.blocked_id=viewer_id)
                 OR (b.blocked_id=u.id AND b.blocker_id=viewer_id)
          )
    );
$$;

-- Every read, including signed media URLs and frozen feed pages, rechecks these
-- rules. Making an account private never relies on rewriting old post visibility.
CREATE OR REPLACE FUNCTION check_post_access(p_post_id UUID, p_user_id UUID)
RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
DECLARE
    p posts%ROWTYPE;
    c communities%ROWTYPE;
BEGIN
    SELECT * INTO p FROM posts WHERE id=p_post_id;
    IF NOT FOUND OR p.status IN ('REMOVED', 'UNDER_REVIEW') THEN
        RETURN FALSE;
    END IF;
    IF p.post_type='STORY' AND (p.published_at IS NULL OR p.published_at<=NOW()-INTERVAL '24 hours') THEN
        RETURN FALSE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM post_authors a JOIN users u ON u.id=a.user_id
        WHERE a.post_id=p.id AND u.status='ACTIVE'
    ) THEN
        RETURN FALSE;
    END IF;
    IF EXISTS (
        SELECT 1 FROM post_authors a JOIN blocked_users b ON
            (b.blocker_id=a.user_id AND b.blocked_id=p_user_id)
            OR (b.blocked_id=a.user_id AND b.blocker_id=p_user_id)
        WHERE a.post_id=p.id
    ) THEN
        RETURN FALSE;
    END IF;
    IF p.community_id IS NOT NULL THEN
        SELECT * INTO c FROM communities WHERE id=p.community_id;
        IF c.expires_at IS NOT NULL AND c.expires_at<=NOW() THEN
            RETURN FALSE;
        END IF;
        IF (c.visibility<>'PUBLIC' OR c.is_paid) AND NOT active_community_member(c.id,p_user_id) THEN
            RETURN FALSE;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM post_authors WHERE post_id=p.id AND user_id=p_user_id) THEN
        RETURN TRUE;
    END IF;
    IF p.status<>'PUBLISHED' THEN
        RETURN FALSE;
    END IF;
    -- A public collaborator or explicit audience cannot expose a private author's work.
    IF EXISTS (
        SELECT 1 FROM post_authors a JOIN users u ON u.id=a.user_id
        WHERE a.post_id=p.id AND u.is_private AND NOT can_view_account_content(u.id,p_user_id)
    ) THEN
        RETURN FALSE;
    END IF;
    IF EXISTS (SELECT 1 FROM post_acl WHERE post_id=p.id AND rule_type='DENY_USER' AND target_user_id=p_user_id) THEN
        RETURN FALSE;
    END IF;
    CASE p.visibility
        WHEN 'PUBLIC' THEN RETURN TRUE;
        WHEN 'FOLLOWERS_ONLY' THEN RETURN EXISTS (
            SELECT 1 FROM follows f JOIN post_authors a ON a.user_id=f.following_id
            WHERE a.post_id=p.id AND f.follower_id=p_user_id
        );
        WHEN 'CLOSE_FRIENDS' THEN RETURN EXISTS (
            SELECT 1 FROM follows f JOIN post_authors a ON a.user_id=f.follower_id
            WHERE a.post_id=p.id AND f.following_id=p_user_id AND f.is_close_friend
        );
        WHEN 'COMMUNITY_ONLY' THEN RETURN active_community_member(p.community_id,p_user_id);
        WHEN 'CUSTOM' THEN RETURN EXISTS (
            SELECT 1 FROM post_acl a WHERE a.post_id=p.id AND (
                (a.rule_type='ALLOW_USER' AND a.target_user_id=p_user_id) OR
                (a.rule_type='ALLOW_LIST' AND EXISTS (
                    SELECT 1 FROM acl_list_members m WHERE m.list_id=a.target_list_id AND m.user_id=p_user_id
                )) OR
                (a.rule_type='ALLOW_COMMUNITY' AND active_community_member(a.target_community_id,p_user_id)) OR
                (a.rule_type='ALLOW_TIER' AND EXISTS (
                    SELECT 1 FROM community_members m WHERE m.tier_id=a.target_tier_id AND m.user_id=p_user_id
                    AND active_community_member(m.community_id,p_user_id)
                ))
            )
        );
        ELSE RETURN FALSE;
    END CASE;
END;
$$;
