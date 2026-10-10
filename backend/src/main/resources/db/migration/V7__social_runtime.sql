CREATE UNIQUE INDEX idx_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX idx_users_email_lower ON users (lower(email));

CREATE TABLE post_engagements (
 post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 kind VARCHAR(10) NOT NULL CHECK(kind IN ('like','save','view')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(post_id,user_id,kind)
);
CREATE INDEX idx_engagements_user ON post_engagements(user_id,kind,created_at DESC);
CREATE TABLE poll_votes (
 post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 block_index INTEGER NOT NULL CHECK(block_index >= 0),
 option_index INTEGER NOT NULL CHECK(option_index >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(post_id,user_id,block_index)
);
CREATE TABLE notifications (
 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
 type VARCHAR(30) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user ON notifications(user_id,created_at DESC);
CREATE TABLE direct_messages (
 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
 sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 content TEXT NOT NULL CHECK(length(content) BETWEEN 1 AND 10000),
 client_id UUID,
 read_at TIMESTAMPTZ,
 expires_at TIMESTAMPTZ,
 deleted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(sender_id,client_id)
);
CREATE INDEX idx_messages_sender ON direct_messages(sender_id,recipient_id,created_at DESC);
CREATE INDEX idx_messages_recipient ON direct_messages(recipient_id,created_at DESC);
CREATE TABLE post_author_invitations (
 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
 post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
 inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 contribution_pct SMALLINT NOT NULL CHECK(contribution_pct>0 AND contribution_pct<100),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(post_id,invitee_id)
);

CREATE OR REPLACE FUNCTION active_community_member(p_community UUID,p_user UUID)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
 SELECT EXISTS (SELECT 1 FROM community_members cm JOIN communities c ON c.id=cm.community_id
 WHERE c.id=p_community AND cm.user_id=p_user
 AND (c.expires_at IS NULL OR c.expires_at>NOW())
 AND (NOT c.is_paid OR cm.role IN ('OWNER','ADMIN') OR
 (cm.subscription_status IN ('ACTIVE','TRIALING') AND
 (cm.subscription_expires_at IS NULL OR cm.subscription_expires_at>NOW()))));
$$;

CREATE OR REPLACE FUNCTION check_post_access(p_post_id UUID,p_user_id UUID)
RETURNS BOOLEAN LANGUAGE plpgsql STABLE AS $$
DECLARE p posts%ROWTYPE; c communities%ROWTYPE;
BEGIN
 SELECT * INTO p FROM posts WHERE id=p_post_id;
 IF NOT FOUND OR p.status IN ('REMOVED','UNDER_REVIEW') THEN RETURN FALSE; END IF;
 IF p.post_type='STORY' AND p.published_at < NOW()-INTERVAL '24 hours' THEN RETURN FALSE; END IF;
 IF EXISTS(SELECT 1 FROM post_authors pa JOIN blocked_users b ON
 ((b.blocker_id=pa.user_id AND b.blocked_id=p_user_id) OR
 (b.blocked_id=pa.user_id AND b.blocker_id=p_user_id)) WHERE pa.post_id=p.id) THEN RETURN FALSE; END IF;
 IF p.community_id IS NOT NULL THEN
  SELECT * INTO c FROM communities WHERE id=p.community_id;
  IF c.expires_at IS NOT NULL AND c.expires_at<=NOW() THEN RETURN FALSE; END IF;
  IF (c.visibility<>'PUBLIC' OR c.is_paid) AND NOT active_community_member(c.id,p_user_id) THEN RETURN FALSE; END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM post_authors WHERE post_id=p.id AND user_id=p_user_id) THEN RETURN TRUE; END IF;
 IF p.status<>'PUBLISHED' THEN RETURN FALSE; END IF;
 IF EXISTS(SELECT 1 FROM post_acl WHERE post_id=p.id AND rule_type='DENY_USER' AND target_user_id=p_user_id) THEN RETURN FALSE; END IF;
 CASE p.visibility
 WHEN 'PUBLIC' THEN RETURN TRUE;
 WHEN 'FOLLOWERS_ONLY' THEN RETURN EXISTS(SELECT 1 FROM follows f JOIN post_authors pa ON pa.user_id=f.following_id WHERE pa.post_id=p.id AND f.follower_id=p_user_id);
 WHEN 'CLOSE_FRIENDS' THEN RETURN EXISTS(SELECT 1 FROM follows f JOIN post_authors pa ON pa.user_id=f.follower_id WHERE pa.post_id=p.id AND f.following_id=p_user_id AND f.is_close_friend);
 WHEN 'COMMUNITY_ONLY' THEN RETURN active_community_member(p.community_id,p_user_id);
 WHEN 'CUSTOM' THEN RETURN EXISTS(SELECT 1 FROM post_acl a WHERE a.post_id=p.id AND (
 (a.rule_type='ALLOW_USER' AND a.target_user_id=p_user_id) OR
 (a.rule_type='ALLOW_LIST' AND EXISTS(SELECT 1 FROM acl_list_members lm WHERE lm.list_id=a.target_list_id AND lm.user_id=p_user_id)) OR
 (a.rule_type='ALLOW_COMMUNITY' AND active_community_member(a.target_community_id,p_user_id)) OR
 (a.rule_type='ALLOW_TIER' AND EXISTS(SELECT 1 FROM community_members cm WHERE cm.tier_id=a.target_tier_id AND cm.user_id=p_user_id AND active_community_member(cm.community_id,p_user_id)))));
 ELSE RETURN FALSE;
 END CASE;
END; $$;
