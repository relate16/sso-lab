package com.ssolab.gallery.artwork;

import static com.ssolab.gallery.artwork.QArtworkEntity.artworkEntity;
import static com.ssolab.gallery.artwork.QArtworkImageEntity.artworkImageEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class ArtworkQueryRepositoryImpl implements ArtworkQueryRepository {
    private static final BigDecimal SMALL_MAX = new BigDecimal("50");
    private static final BigDecimal MEDIUM_MAX = new BigDecimal("100");

    private final JPAQueryFactory queries;

    public ArtworkQueryRepositoryImpl(JPAQueryFactory queries) {
        this.queries = queries;
    }

    @Override
    public List<ArtworkEntity> findLatestPublished(int limit) {
        List<UUID> ids = queries.select(artworkEntity.id)
            .from(artworkEntity)
            .where(publicArtwork())
            .orderBy(artworkEntity.publishedAt.desc(), artworkEntity.id.asc())
            .limit(Math.max(0, limit))
            .fetch();
        return fetchInOrder(ids);
    }

    @Override
    public Optional<ArtworkEntity> findPublishedByPublicId(UUID publicId) {
        return fetchOne(publicId, true);
    }

    @Override
    public Optional<ArtworkEntity> findByPublicId(UUID publicId) {
        return fetchOne(publicId, false);
    }

    @Override
    public List<ArtworkEntity> findAllForStudio() {
        List<UUID> ids = queries.select(artworkEntity.id)
            .from(artworkEntity)
            .orderBy(artworkEntity.displayOrder.asc(), artworkEntity.createdAt.asc(),
                artworkEntity.id.asc())
            .fetch();
        return fetchInOrder(ids);
    }

    @Override
    public List<ArtworkEntity> findAllByPublicIds(Collection<UUID> publicIds) {
        if (publicIds.isEmpty()) return List.of();
        return queries.selectFrom(artworkEntity)
            .where(artworkEntity.publicId.in(publicIds))
            .fetch();
    }

    @Override
    public Page<ArtworkEntity> search(ArtworkSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder predicate = predicates(criteria);
        Long total = queries.select(artworkEntity.count())
            .from(artworkEntity)
            .where(predicate)
            .fetchOne();
        if (total == null || total == 0) return new PageImpl<>(List.of(), pageable, 0);

        // Page root IDs first: collection fetch joins must not paginate in memory.
        List<UUID> ids = queries.select(artworkEntity.id)
            .from(artworkEntity)
            .where(predicate)
            .orderBy(order(criteria.order()))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        return new PageImpl<>(fetchInOrder(ids), pageable, total);
    }

    private Optional<ArtworkEntity> fetchOne(UUID publicId, boolean publishedOnly) {
        BooleanBuilder predicate = new BooleanBuilder(artworkEntity.publicId.eq(publicId));
        if (publishedOnly) predicate.and(publicArtwork());
        return queries.selectFrom(artworkEntity)
            .leftJoin(artworkEntity.images, artworkImageEntity).fetchJoin()
            .where(predicate)
            .distinct()
            .fetch()
            .stream()
            .findFirst();
    }

    private List<ArtworkEntity> fetchInOrder(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        var byId = new HashMap<UUID, ArtworkEntity>();
        queries.selectFrom(artworkEntity)
            .leftJoin(artworkEntity.images, artworkImageEntity).fetchJoin()
            .where(artworkEntity.id.in(ids))
            .distinct()
            .fetch()
            .forEach(artwork -> byId.put(artwork.getId(), artwork));
        return ids.stream().map(byId::get).toList();
    }

    private BooleanBuilder predicates(ArtworkSearchCriteria criteria) {
        BooleanBuilder predicate = new BooleanBuilder(publicArtwork());
        if (criteria.query() != null && !criteria.query().isBlank()) {
            String term = "%" + criteria.query().trim().toLowerCase(Locale.ROOT) + "%";
            predicate.and(artworkEntity.title.lower().like(term)
                .or(artworkEntity.description.lower().like(term)));
        }
        if (criteria.saleStatus() != null) {
            predicate.and(artworkEntity.saleStatus.eq(criteria.saleStatus()));
        }
        if (criteria.minPrice() != null) predicate.and(artworkEntity.price.goe(criteria.minPrice()));
        if (criteria.maxPrice() != null) predicate.and(artworkEntity.price.loe(criteria.maxPrice()));
        if (criteria.sizeBand() != null) predicate.and(size(criteria.sizeBand()));
        return predicate;
    }

    private Predicate publicArtwork() {
        return artworkEntity.published.isTrue().and(artworkEntity.archivedAt.isNull());
    }

    private Predicate size(ArtworkSearchCriteria.SizeBand sizeBand) {
        return switch (sizeBand) {
            case SMALL -> artworkEntity.widthCm.loe(SMALL_MAX)
                .and(artworkEntity.heightCm.loe(SMALL_MAX));
            case MEDIUM -> artworkEntity.widthCm.loe(MEDIUM_MAX)
                .and(artworkEntity.heightCm.loe(MEDIUM_MAX))
                .and(artworkEntity.widthCm.gt(SMALL_MAX)
                    .or(artworkEntity.heightCm.gt(SMALL_MAX)));
            case LARGE -> artworkEntity.widthCm.gt(MEDIUM_MAX)
                .or(artworkEntity.heightCm.gt(MEDIUM_MAX));
        };
    }

    private OrderSpecifier<?>[] order(ArtworkSearchCriteria.Order order) {
        return switch (order) {
            case PRICE_ASC -> new OrderSpecifier<?>[] {
                artworkEntity.price.asc().nullsLast(), artworkEntity.id.asc()
            };
            case PRICE_DESC -> new OrderSpecifier<?>[] {
                artworkEntity.price.desc().nullsLast(), artworkEntity.id.asc()
            };
            case RECENT -> new OrderSpecifier<?>[] {
                artworkEntity.publishedAt.desc(), artworkEntity.displayOrder.asc(),
                artworkEntity.id.asc()
            };
        };
    }
}
