CREATE TABLE event_rsvps (
 event_id UUID NOT NULL REFERENCES community_events(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(event_id,user_id)
);
CREATE INDEX idx_event_rsvps_user ON event_rsvps(user_id,event_id);
ALTER TABLE community_events ADD CONSTRAINT chk_event_dates CHECK(ends_at > starts_at);
ALTER TABLE community_events ADD CONSTRAINT chk_event_capacity CHECK(max_attendees IS NULL OR max_attendees > 0);
