# Gallery query conventions

OpenFeign QueryDSL 7.7 is configured only in `gallery-server`. Other backend modules
do not receive QueryDSL dependencies or annotation processors.

- `GalleryQuerydslConfig` provides `JPAQueryFactory` using Spring's transactional EntityManager.
- `ArtworkQueryRepository`, `ArtworkImageQueryRepository`, and `InquiryQueryRepository`
  are Spring Data repository fragments. Their `Impl` classes contain QueryDSL queries.
- Keep ordinary persistence operations (`save`, `delete`, `flush`, `findById`, `count`)
  on `JpaRepository`.
- Keep the PostgreSQL atomic analytics upsert/insert and analytics aggregates on
  `JdbcTemplate`. QueryDSL JPA does not replace these SQL operations.
- Paginated artwork queries select ordered IDs first, then fetch their image
  collections without a limit, and restore ID order. Do not paginate a collection
  fetch join: that can produce in-memory pagination or incorrect page sizes.
- Public queries require both `published = true` and `archivedAt IS NULL`.
  Studio queries intentionally include drafts and archived artworks.

## Generated sources

The standard Gradle Java annotation processor generates Q classes under
`build/generated/sources/annotationProcessor/java/main`. They are build outputs and
are not committed. A fresh checkout only needs the normal compile task; no custom
QueryDSL Gradle plugin or separate generation task is required.

```powershell
.\gradlew.bat :backend:gallery-server:compileJava
.\gradlew.bat :backend:gallery-server:test
```

`GalleryQueryRepositoryIntegrationTest` runs the query contract against H2.
It also applies the image storage-key format CHECK from Flyway, using H2's regex
syntax, so fixtures with invalid keys fail locally as well as on PostgreSQL.
`GalleryServerPostgresqlIntegrationTest` runs the same contract plus Flyway checks
against PostgreSQL 17 using Testcontainers when Docker is available. H2 tests do
not validate PostgreSQL migrations or PostgreSQL-specific analytics SQL.
