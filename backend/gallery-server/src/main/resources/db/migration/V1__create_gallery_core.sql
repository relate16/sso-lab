CREATE TABLE artworks (
    id UUID PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(5000) NOT NULL DEFAULT '',
    production_year INTEGER,
    material VARCHAR(240),
    width_cm NUMERIC(8, 2) NOT NULL,
    height_cm NUMERIC(8, 2) NOT NULL,
    price NUMERIC(14, 2),
    sale_status VARCHAR(24) NOT NULL,
    frame_type VARCHAR(24) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT artworks_title_ck CHECK (length(trim(title)) BETWEEN 1 AND 160),
    CONSTRAINT artworks_description_ck CHECK (length(description) <= 5000),
    CONSTRAINT artworks_year_ck CHECK (production_year IS NULL OR production_year BETWEEN 1000 AND 9999),
    CONSTRAINT artworks_width_ck CHECK (width_cm > 0),
    CONSTRAINT artworks_height_ck CHECK (height_cm > 0),
    CONSTRAINT artworks_price_ck CHECK (price IS NULL OR price >= 0),
    CONSTRAINT artworks_sale_status_ck CHECK (sale_status IN ('NOT_FOR_SALE', 'AVAILABLE', 'RESERVED', 'SOLD')),
    CONSTRAINT artworks_frame_type_ck CHECK (frame_type IN ('NONE', 'MAT_BOARD', 'ACRYLIC_BOX', 'FLOATING_FRAME')),
    CONSTRAINT artworks_published_at_ck CHECK (NOT published OR published_at IS NOT NULL)
);

CREATE INDEX artworks_public_listing_ix
    ON artworks (published, published_at DESC, display_order, id);
CREATE INDEX artworks_sale_status_ix ON artworks (sale_status) WHERE published = TRUE;

CREATE TABLE artwork_images (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    original_path VARCHAR(240) NOT NULL,
    web_image_path VARCHAR(240) NOT NULL,
    thumbnail_path VARCHAR(240) NOT NULL,
    original_filename VARCHAR(240) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    width_px INTEGER NOT NULL,
    height_px INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT artwork_images_width_ck CHECK (width_px > 0),
    CONSTRAINT artwork_images_height_ck CHECK (height_px > 0),
    CONSTRAINT artwork_images_content_type_ck CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT artwork_images_storage_key_ck CHECK (storage_key ~ '^[a-f0-9-]{36}$')
);

CREATE INDEX artwork_images_order_ix ON artwork_images (artwork_id, sort_order, id);
CREATE UNIQUE INDEX artwork_images_one_primary_ix
    ON artwork_images (artwork_id) WHERE is_primary = TRUE;

CREATE TABLE inquiries (
    id UUID PRIMARY KEY,
    artwork_id UUID NOT NULL REFERENCES artworks(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(40),
    message VARCHAR(3000) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT inquiries_name_ck CHECK (length(trim(name)) BETWEEN 1 AND 100),
    CONSTRAINT inquiries_email_ck CHECK (length(trim(email)) BETWEEN 3 AND 254),
    CONSTRAINT inquiries_message_ck CHECK (length(trim(message)) BETWEEN 1 AND 3000),
    CONSTRAINT inquiries_status_ck CHECK (status IN ('NEW', 'READ', 'CLOSED'))
);

CREATE INDEX inquiries_studio_listing_ix ON inquiries (status, created_at DESC, id);
