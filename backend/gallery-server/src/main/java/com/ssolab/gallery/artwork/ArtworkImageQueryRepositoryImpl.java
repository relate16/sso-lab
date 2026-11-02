package com.ssolab.gallery.artwork;

import static com.ssolab.gallery.artwork.QArtworkEntity.artworkEntity;
import static com.ssolab.gallery.artwork.QArtworkImageEntity.artworkImageEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;

public class ArtworkImageQueryRepositoryImpl implements ArtworkImageQueryRepository {
    private final JPAQueryFactory queries;

    public ArtworkImageQueryRepositoryImpl(JPAQueryFactory queries) {
        this.queries = queries;
    }

    @Override
    public Optional<ArtworkImageEntity> findPublishedByStorageKey(String storageKey) {
        return Optional.ofNullable(queries.selectFrom(artworkImageEntity)
            .join(artworkImageEntity.artwork, artworkEntity)
            .where(artworkImageEntity.storageKey.eq(storageKey),
                artworkEntity.published.isTrue(), artworkEntity.archivedAt.isNull())
            .fetchOne());
    }

    @Override
    public Optional<ArtworkImageEntity> findByStorageKey(String storageKey) {
        return Optional.ofNullable(queries.selectFrom(artworkImageEntity)
            .where(artworkImageEntity.storageKey.eq(storageKey))
            .fetchOne());
    }
}
