-- ═══════════════════════════════════════════════════════════════════
-- V6: Copyright Claims, Media Hashes, Trust & Safety
-- ═══════════════════════════════════════════════════════════════════

-- ── Media Assets (tracking all uploaded media) ──
CREATE TABLE media_assets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    uploader_id     UUID NOT NULL REFERENCES users(id),
    original_url    VARCHAR(512) NOT NULL,

    -- Transcoded variants
    variants        JSONB DEFAULT '{}'::jsonb,
    -- e.g. {"hls":"s3://...","dash":"s3://...","thumb":"s3://...","720p":"s3://..."}

    -- File metadata
    media_type      VARCHAR(20) NOT NULL
        CHECK (media_type IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT')),
    mime_type       VARCHAR(100),
    file_size_bytes BIGINT,
    width           INTEGER,
    height          INTEGER,
    duration_ms     BIGINT,

    -- Provenance
    sha256_hash     VARCHAR(64) NOT NULL,
    merkle_root     VARCHAR(64),
    blockchain_tx   VARCHAR(128),
    provenance_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (provenance_status IN ('NONE', 'HASHED', 'BATCHED', 'ANCHORED')),

    -- Upload tracking (TUS)
    tus_upload_id   VARCHAR(255),
    upload_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (upload_status IN ('PENDING', 'UPLOADING', 'PROCESSING', 'READY', 'FAILED')),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_uploader ON media_assets (uploader_id);
CREATE INDEX idx_media_hash     ON media_assets (sha256_hash);
CREATE INDEX idx_media_status   ON media_assets (upload_status);
CREATE INDEX idx_media_type     ON media_assets (media_type);

-- ── Copyright Claims ──
CREATE TABLE copyright_claims (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- The claimant
    claimant_id         UUID NOT NULL REFERENCES users(id),
    claimant_email      VARCHAR(255) NOT NULL,

    -- The content being claimed
    target_post_id      UUID REFERENCES posts(id) ON DELETE SET NULL,
    target_media_id     UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    target_user_id      UUID REFERENCES users(id),        -- post author

    -- Claim details
    claim_type          VARCHAR(30) NOT NULL
        CHECK (claim_type IN ('COPYRIGHT', 'TRADEMARK', 'IDENTITY', 'OTHER')),
    description         TEXT NOT NULL,
    evidence_urls       JSONB DEFAULT '[]'::jsonb,
    original_work_url   VARCHAR(512),

    -- Media hash comparison (automated)
    claimant_media_hash VARCHAR(64),
    target_media_hash   VARCHAR(64),
    hash_match          BOOLEAN,

    -- Workflow Status
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',          -- just filed
            'REVIEWING',        -- under review by trust & safety
            'UPHELD',           -- claim valid → content removed
            'REJECTED',         -- claim invalid
            'COUNTER_CLAIMED',  -- target filed counter-claim
            'COUNTER_REVIEWING',-- counter-claim under review
            'RESOLVED',         -- final resolution
            'WITHDRAWN'         -- claimant withdrew
        )),

    -- Resolution
    resolution_notes    TEXT,
    resolved_by         UUID REFERENCES users(id),
    resolved_at         TIMESTAMPTZ,

    -- Timestamps
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claims_claimant ON copyright_claims (claimant_id);
CREATE INDEX idx_claims_target   ON copyright_claims (target_post_id) WHERE target_post_id IS NOT NULL;
CREATE INDEX idx_claims_status   ON copyright_claims (status);
CREATE INDEX idx_claims_hashes   ON copyright_claims (claimant_media_hash, target_media_hash)
    WHERE claimant_media_hash IS NOT NULL;

CREATE TRIGGER trg_claims_updated_at
    BEFORE UPDATE ON copyright_claims
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Counter-Claims ──
CREATE TABLE counter_claims (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    claim_id            UUID NOT NULL REFERENCES copyright_claims(id) ON DELETE CASCADE,
    respondent_id       UUID NOT NULL REFERENCES users(id),
    statement           TEXT NOT NULL,
    evidence_urls       JSONB DEFAULT '[]'::jsonb,
    legal_name          VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'REVIEWING', 'ACCEPTED', 'REJECTED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_counter_claims_claim ON counter_claims (claim_id);

-- ── Content Reports (general moderation) ──
CREATE TABLE content_reports (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id     UUID NOT NULL REFERENCES users(id),
    target_type     VARCHAR(20) NOT NULL
        CHECK (target_type IN ('POST', 'COMMENT', 'USER', 'COMMUNITY', 'MESSAGE')),
    target_id       UUID NOT NULL,
    reason          VARCHAR(50) NOT NULL
        CHECK (reason IN (
            'SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE',
            'NUDITY', 'MISINFORMATION', 'SELF_HARM', 'ILLEGAL',
            'IMPERSONATION', 'COPYRIGHT', 'OTHER'
        )),
    description     TEXT,
    evidence_urls   JSONB DEFAULT '[]'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'REVIEWING', 'ACTIONED', 'DISMISSED')),
    action_taken    VARCHAR(50),
    reviewed_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_reports_reporter ON content_reports (reporter_id);
CREATE INDEX idx_reports_target   ON content_reports (target_type, target_id);
CREATE INDEX idx_reports_status   ON content_reports (status);
