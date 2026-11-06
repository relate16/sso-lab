package com.ssolab.gallery;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:gallery-query;MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS gallery",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.hikari.connection-init-sql=SET SCHEMA gallery",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
    "sso.oidc-client.client-secret=gallery-query-test-secret",
    "gallery.storage.root=build/tmp/gallery-query-integration"
})
class GalleryQueryRepositoryIntegrationTest extends GalleryAnalyticsRepositoryContract {
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void enforceImageStorageKeyConstraint() {
        // Hibernate's generated schema omits this Flyway CHECK. Keep its format
        // requirement in H2 too so invalid fixtures fail locally, before CI.
        jdbc.execute("""
            ALTER TABLE gallery.artwork_images
            ADD CONSTRAINT IF NOT EXISTS artwork_images_storage_key_ck
            CHECK (REGEXP_LIKE(storage_key, '^[a-f0-9-]{36}$'))
            """);
    }
}
