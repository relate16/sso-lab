package com.ssolab.gallery.inquiry;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepository extends JpaRepository<InquiryEntity, UUID> {
    Page<InquiryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
