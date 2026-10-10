-- Client-generated IDs are scoped to the authenticated user. The event and
-- the engagement change commit together so retrying cannot apply it twice.
CREATE TABLE offline_events (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    post_id UUID NOT NULL,
    event_type VARCHAR(10) NOT NULL CHECK (event_type IN ('LIKE','UNLIKE','SAVE','UNSAVE','VIEW')),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id,event_id)
);
CREATE INDEX idx_offline_events_applied ON offline_events(applied_at);
