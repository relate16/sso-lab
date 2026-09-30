ALTER TABLE artworks
    ADD COLUMN carousel_focal_x NUMERIC(5, 4) NOT NULL DEFAULT 0.5000,
    ADD COLUMN carousel_focal_y NUMERIC(5, 4) NOT NULL DEFAULT 0.5000,
    ADD COLUMN carousel_zoom NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    ADD CONSTRAINT artworks_carousel_focal_x_ck CHECK (carousel_focal_x BETWEEN 0 AND 1),
    ADD CONSTRAINT artworks_carousel_focal_y_ck CHECK (carousel_focal_y BETWEEN 0 AND 1),
    ADD CONSTRAINT artworks_carousel_zoom_ck CHECK (carousel_zoom BETWEEN 1 AND 3);
