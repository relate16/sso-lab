package com.ssolab.gallery.inquiry;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<InquiryEntity, UUID> { }
