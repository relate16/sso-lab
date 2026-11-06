package com.ssolab.gallery.analytics;

import static com.ssolab.gallery.analytics.QArtworkViewEventEntity.artworkViewEventEntity;
import static com.ssolab.gallery.artwork.QArtworkEntity.artworkEntity;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssolab.gallery.api.GalleryDtos;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ArtworkAnalyticsQueryRepositoryImpl implements ArtworkAnalyticsQueryRepository {
    private final JPAQueryFactory queries;

    public ArtworkAnalyticsQueryRepositoryImpl(JPAQueryFactory queries) {
        this.queries = queries;
    }

    @Override
    public ArtworkAnalyticsDtos.Metric metric(LocalDate from) {
        var event = artworkViewEventEntity;
        return queries.select(Projections.constructor(ArtworkAnalyticsDtos.Metric.class,
                event.count(), event.visitorHash.countDistinct()))
            .from(event).where(from == null ? null : event.viewedDate.goe(from)).fetchOne();
    }

    @Override
    public List<ArtworkAnalyticsDtos.Daily> daily(LocalDate from) {
        var event = artworkViewEventEntity;
        return queries.select(Projections.constructor(ArtworkAnalyticsDtos.Daily.class,
                event.viewedDate, event.count(), event.visitorHash.countDistinct()))
            .from(event).where(event.viewedDate.goe(from))
            .groupBy(event.viewedDate).orderBy(event.viewedDate.asc()).fetch();
    }

    @Override
    public List<ArtworkAnalyticsDtos.TopArtwork> topArtworks() {
        var event = artworkViewEventEntity;
        var artwork = artworkEntity;
        return queries.select(Projections.constructor(ArtworkAnalyticsDtos.TopArtwork.class,
                artwork.publicId, artwork.title, artwork.archivedAt.isNotNull(),
                event.count(), event.visitorHash.countDistinct()))
            .from(event).join(event.artwork, artwork)
            .groupBy(artwork.id, artwork.publicId, artwork.title, artwork.archivedAt)
            .orderBy(event.count().desc(), event.visitorHash.countDistinct().desc(), artwork.title.asc())
            .limit(20).fetch();
    }

    @Override
    public List<ArtworkAnalyticsDtos.Breakdown> surfaces() {
        return breakdown(artworkViewEventEntity.surface);
    }

    @Override
    public List<ArtworkAnalyticsDtos.Breakdown> sources() {
        return breakdown(artworkViewEventEntity.entrySource);
    }

    private <E extends Enum<E>> List<ArtworkAnalyticsDtos.Breakdown> breakdown(EnumPath<E> key) {
        var event = artworkViewEventEntity;
        var views = event.count();
        var visitors = event.visitorHash.countDistinct();
        return queries.select(key, views, visitors).from(event).groupBy(key)
            .orderBy(views.desc()).fetch().stream()
            .map(row -> new ArtworkAnalyticsDtos.Breakdown(row.get(key).name(),
                row.get(views), row.get(visitors))).toList();
    }

    @Override
    public List<ArtworkAnalyticsDtos.Breakdown> referrers() {
        var event = artworkViewEventEntity;
        var referrer = event.referrerHost.coalesce("직접 접속");
        return queries.select(Projections.constructor(ArtworkAnalyticsDtos.Breakdown.class,
                referrer, event.count(), event.visitorHash.countDistinct()))
            .from(event).groupBy(referrer).orderBy(event.count().desc()).limit(20).fetch();
    }

    @Override
    public GalleryDtos.Page<ArtworkAnalyticsDtos.RecentView> recentViews(int page, int size) {
        var event = artworkViewEventEntity;
        var artwork = artworkEntity;
        Long count = queries.select(event.count()).from(event).fetchOne();
        long total = count == null ? 0 : count;
        var content = queries.select(event.id, artwork.publicId, artwork.title,
                artwork.archivedAt, event.surface, event.viewedAt, event.visitorIp)
            .from(event).join(event.artwork, artwork)
            .orderBy(event.viewedAt.desc(), event.id.desc())
            .offset((long) page * size).limit(size).fetch().stream()
            .map(row -> {
                var ip = row.get(event.visitorIp);
                return new ArtworkAnalyticsDtos.RecentView(row.get(event.id), row.get(artwork.publicId),
                    row.get(artwork.title), row.get(artwork.archivedAt) != null,
                    row.get(event.surface), row.get(event.viewedAt),
                    ip == null ? null : ip.getHostAddress());
            }).toList();
        return new GalleryDtos.Page<>(content, page, size, total,
            (int) Math.ceil((double) total / size));
    }
}
