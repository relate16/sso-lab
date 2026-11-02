package com.ssolab.gallery.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryQueryRepository {
    Page<InquiryEntity> findNewestFirst(Pageable pageable);
}
