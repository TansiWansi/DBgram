-- ═══════════════════════════════════════════════════════════════════
-- V5: Billing — Subscriptions, Payments, Creator Marketplace
-- ═══════════════════════════════════════════════════════════════════

-- ── Stripe Customer Mapping ──
CREATE TABLE stripe_customers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    stripe_customer_id VARCHAR(255) NOT NULL UNIQUE,
    default_payment_method VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stripe_customers_user   ON stripe_customers (user_id);
CREATE INDEX idx_stripe_customers_stripe ON stripe_customers (stripe_customer_id);

-- ── Payment History ──
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(id),
    stripe_payment_id   VARCHAR(255) NOT NULL UNIQUE,
    amount_cents        INTEGER NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    status              VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    payment_type        VARCHAR(30) NOT NULL
        CHECK (payment_type IN ('SUBSCRIPTION', 'TIP', 'MARKETPLACE_PURCHASE')),
    description         VARCHAR(255),
    metadata            JSONB DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user   ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_stripe ON payments (stripe_payment_id);
CREATE INDEX idx_payments_status ON payments (status);

-- ── Creator Marketplace Listings ──
CREATE TABLE listings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    seller_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    price_cents     INTEGER NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Product type
    listing_type    VARCHAR(30) NOT NULL
        CHECK (listing_type IN (
            'DIGITAL_DOWNLOAD',   -- presets, templates, ebooks
            'SHOUTOUT',           -- personalized video
            'CONSULTATION',       -- 1-on-1 booking
            'MERCHANDISE',        -- physical products
            'CUSTOM'
        )),

    -- Content/delivery
    media_urls      JSONB DEFAULT '[]'::jsonb,
    preview_url     VARCHAR(512),
    delivery_info   JSONB DEFAULT '{}'::jsonb,

    -- Metadata
    tags            JSONB DEFAULT '[]'::jsonb,
    is_featured     BOOLEAN NOT NULL DEFAULT FALSE,
    stock_count     INTEGER,     -- NULL = unlimited

    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SOLD_OUT', 'ARCHIVED')),

    -- Analytics
    view_count      BIGINT NOT NULL DEFAULT 0,
    purchase_count  BIGINT NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listings_seller  ON listings (seller_id);
CREATE INDEX idx_listings_type    ON listings (listing_type);
CREATE INDEX idx_listings_status  ON listings (status);
CREATE INDEX idx_listings_price   ON listings (price_cents);
CREATE INDEX idx_listings_tags    ON listings USING GIN (tags);

CREATE TRIGGER trg_listings_updated_at
    BEFORE UPDATE ON listings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Orders (Marketplace purchases) ──
CREATE TABLE orders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id      UUID NOT NULL REFERENCES listings(id),
    buyer_id        UUID NOT NULL REFERENCES users(id),
    seller_id       UUID NOT NULL REFERENCES users(id),
    payment_id      UUID REFERENCES payments(id),
    amount_cents    INTEGER NOT NULL,
    platform_fee_cents INTEGER NOT NULL DEFAULT 0,
    seller_payout_cents INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'DELIVERED', 'COMPLETED', 'REFUNDED', 'DISPUTED')),
    delivery_data   JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_buyer    ON orders (buyer_id, created_at DESC);
CREATE INDEX idx_orders_seller   ON orders (seller_id, created_at DESC);
CREATE INDEX idx_orders_listing  ON orders (listing_id);
CREATE INDEX idx_orders_status   ON orders (status);

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Webhook Event Log (idempotency) ──
CREATE TABLE stripe_webhook_events (
    event_id        VARCHAR(255) PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
