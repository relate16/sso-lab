ALTER TABLE artworks
    ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX artworks_active_listing_ix
    ON artworks (published, published_at DESC, display_order, id)
    WHERE archived_at IS NULL;

CREATE TABLE artwork_view_events (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    visitor_hash CHAR(64) NOT NULL,
    surface VARCHAR(32) NOT NULL,
    entry_source VARCHAR(32) NOT NULL,
    referrer_host VARCHAR(253),
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    viewed_date DATE NOT NULL,
    CONSTRAINT artwork_view_events_surface_ck CHECK (surface IN (
        'HOME_CAROUSEL', 'HOME_EXHIBITION', 'WORKS_GRID', 'WORKS_LIGHTBOX', 'DETAIL_SCENE'
    )),
    CONSTRAINT artwork_view_events_entry_source_ck CHECK (entry_source IN (
        'DIRECT', 'HOME_CAROUSEL', 'HOME_EXHIBITION', 'WORKS_GRID',
        'WORKS_LIGHTBOX', 'INTERNAL', 'EXTERNAL', 'UNKNOWN'
    )),
    CONSTRAINT artwork_view_events_visitor_hash_ck CHECK (visitor_hash ~ '^[a-f0-9]{64}$')
);

CREATE INDEX artwork_view_events_artwork_time_ix
    ON artwork_view_events (artwork_id, viewed_at DESC);
CREATE INDEX artwork_view_events_artwork_daily_unique_ix
    ON artwork_view_events (artwork_id, viewed_date, visitor_hash);
CREATE INDEX artwork_view_events_daily_ranking_ix
    ON artwork_view_events (viewed_date, artwork_id);
CREATE INDEX artwork_view_events_artwork_visitor_ix
    ON artwork_view_events (artwork_id, visitor_hash);

CREATE TABLE artwork_view_dedup (
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    visitor_hash CHAR(64) NOT NULL,
    surface VARCHAR(32) NOT NULL,
    last_counted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (artwork_id, visitor_hash, surface),
    CONSTRAINT artwork_view_dedup_surface_ck CHECK (surface IN (
        'HOME_CAROUSEL', 'HOME_EXHIBITION', 'WORKS_GRID', 'WORKS_LIGHTBOX', 'DETAIL_SCENE'
    )),
    CONSTRAINT artwork_view_dedup_visitor_hash_ck CHECK (visitor_hash ~ '^[a-f0-9]{64}$')
);

CREATE INDEX artwork_view_dedup_last_counted_ix
    ON artwork_view_dedup (last_counted_at);
