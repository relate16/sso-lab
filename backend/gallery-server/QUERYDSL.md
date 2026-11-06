# Gallery query conventions

OpenFeign QueryDSL 7.7 is configured only in `gallery-server`. Other backend modules
do not receive QueryDSL dependencies or annotation processors.

- `GalleryQuerydslConfig` provides `JPAQueryFactory` using Spring's transactional EntityManager.
- `ArtworkQueryRepository`, `ArtworkImageQueryRepository`, and `InquiryQueryRepository`
  are Spring Data repository fragments. Their `Impl` classes contain QueryDSL queries.
- Keep ordinary persistence operations (`save`, `delete`, `flush`, `findById`, `count`)
  on `JpaRepository`.
- `ArtworkAnalyticsQueryRepository` contains all analytics reads and aggregates
  using QueryDSL. `ArtworkAnalyticsService` calculates reporting windows and
  composes the response without JDBC or query implementation code.
- `ArtworkViewEventEntity` is an immutable read mapping of the existing event
  table; no persistence repository or write API is exposed for it. Hibernate maps
  its `InetAddress` field to PostgreSQL `INET`. IPs remain strings in API responses
  (IPv6 uses Java's expanded textual representation).
- Keep only the atomic analytics dedup upsert/event insert in `ArtworkViewService`
  on `JdbcTemplate`. Its PostgreSQL CTE and conditional `ON CONFLICT` enforce the
  rolling dedup window atomically; ordinary reads do not need this exception.
- Group referrer analytics by the stored column and apply the missing-referrer
  display label after fetching. Repeating a parameterized COALESCE in SELECT and
  GROUP BY can produce distinct bind parameters that PostgreSQL does not consider
  the same grouping expression.
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

`GalleryQueryRepositoryIntegrationTest` runs the query and analytics contracts against H2.
It also applies the image storage-key format CHECK from Flyway, using H2's regex
syntax, so fixtures with invalid keys fail locally as well as on PostgreSQL.
The analytics contract also compares Hibernate's visitor-hash JDBC mapping with
the physical column metadata. This catches CHAR/VARCHAR mismatches locally even
when Hibernate-generated H2 DDL happens to use the intended column type.
`GalleryServerPostgresqlIntegrationTest` runs the same contract plus Flyway checks
against PostgreSQL 17 using Testcontainers when Docker is available. H2 tests do
not validate PostgreSQL migrations, native INET mapping or the atomic analytics
write SQL. The analytics contract verifies empty results, distinct visitor counts,
date boundaries, archived works, breakdowns, and stable recent-view pagination.
