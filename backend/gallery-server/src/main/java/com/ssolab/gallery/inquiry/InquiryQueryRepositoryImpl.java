package com.ssolab.gallery.inquiry;

import static com.ssolab.gallery.artwork.QArtworkEntity.artworkEntity;
import static com.ssolab.gallery.inquiry.QInquiryEntity.inquiryEntity;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class InquiryQueryRepositoryImpl implements InquiryQueryRepository {
    private final JPAQueryFactory queries;

    public InquiryQueryRepositoryImpl(JPAQueryFactory queries) {
        this.queries = queries;
    }

    @Override
    public Page<InquiryEntity> findNewestFirst(Pageable pageable) {
        Long total = queries.select(inquiryEntity.count())
            .from(inquiryEntity)
            .fetchOne();
        if (total == null || total == 0) return new PageImpl<>(List.of(), pageable, 0);

        List<InquiryEntity> content = queries.selectFrom(inquiryEntity)
            .join(inquiryEntity.artwork, artworkEntity).fetchJoin()
            .orderBy(inquiryEntity.createdAt.desc(), inquiryEntity.id.asc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        return new PageImpl<>(content, pageable, total);
    }
}
