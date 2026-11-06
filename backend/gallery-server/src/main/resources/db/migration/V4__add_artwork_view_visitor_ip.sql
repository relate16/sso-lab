ALTER TABLE artwork_view_events ADD COLUMN visitor_ip INET;

CREATE INDEX artwork_view_events_recent_idx
    ON artwork_view_events (viewed_at DESC, id DESC);
