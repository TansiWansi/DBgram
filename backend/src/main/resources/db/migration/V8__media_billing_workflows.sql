CREATE TABLE media_uploads (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    upload_length BIGINT NOT NULL CHECK(upload_length >= 0),
    upload_offset BIGINT NOT NULL DEFAULT 0 CHECK(upload_offset >= 0 AND upload_offset <= upload_length),
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    metadata VARCHAR(4096),
    status VARCHAR(20) NOT NULL,
    sha256 VARCHAR(64), merkle_root VARCHAR(64), leaf_hashes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), expires_at TIMESTAMPTZ NOT NULL,
    transcode_status VARCHAR(20), transcode_error VARCHAR(512), transcode_started_at TIMESTAMPTZ, revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_upload_owner ON media_uploads(owner_id,created_at DESC);
CREATE INDEX idx_upload_work ON media_uploads(status,transcode_status);

CREATE TABLE stripe_events (
    id VARCHAR(255) PRIMARY KEY, event_type VARCHAR(100) NOT NULL,
    event_created BIGINT NOT NULL, processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE billing_entitlements (
    subscription_id VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id), community_id UUID REFERENCES communities(id),
    customer_id VARCHAR(255), status VARCHAR(30) NOT NULL,
    current_period_end TIMESTAMPTZ, last_event_created BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_entitlement_user ON billing_entitlements(user_id,status);
CREATE TABLE copyright_claim_events (
    id UUID PRIMARY KEY, claim_id UUID NOT NULL REFERENCES copyright_claims(id),
    actor_id UUID NOT NULL REFERENCES users(id), action VARCHAR(30) NOT NULL,
    notes TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_claim_event ON copyright_claim_events(claim_id,created_at);
CREATE TABLE post_view_events (
    post_id UUID NOT NULL REFERENCES posts(id), viewer_id UUID NOT NULL REFERENCES users(id),
    viewed_on DATE NOT NULL DEFAULT CURRENT_DATE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(post_id,viewer_id,viewed_on)
);
