package com.ssolab.gallery;

import org.springframework.boot.test.context.SpringBootTest;

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
class GalleryQueryRepositoryIntegrationTest extends GalleryQueryRepositoryContract { }
