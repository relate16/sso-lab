package com.ssolab.gallery.inquiry;

import com.ssolab.gallery.api.GalleryDtos;
import com.ssolab.gallery.api.GalleryNotFoundException;
import com.ssolab.gallery.api.GalleryValidationException;
import com.ssolab.gallery.artwork.ArtworkRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InquiryService {
    private final InquiryRepository inquiries;
    private final ArtworkRepository artworks;
    private final Clock clock = Clock.systemUTC();
    public InquiryService(InquiryRepository inquiries, ArtworkRepository artworks) {
        this.inquiries = inquiries; this.artworks = artworks;
    }
    @Transactional
    public UUID create(GalleryDtos.InquiryCreate request) {
        if (!request.privacyAgreed()) throw new GalleryValidationException("privacy agreement is required");
        var artwork = artworks.findByPublicIdAndPublishedTrue(request.artworkId())
            .orElseThrow(() -> new GalleryNotFoundException("artwork not found"));
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        var inquiry = new InquiryEntity(artwork, request.name().trim(), request.email().trim(),
            phone, request.message().trim(), clock.instant());
        return inquiries.save(inquiry).getId();
    }
    @Transactional(readOnly = true)
    public GalleryDtos.Page<GalleryDtos.Inquiry> list(int page, int size) {
        var result = inquiries.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
        return new GalleryDtos.Page<>(result.stream().map(GalleryDtos.Inquiry::from).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public void status(UUID id, InquiryStatus status) {
        InquiryEntity inquiry = inquiries.findById(id)
            .orElseThrow(() -> new GalleryNotFoundException("inquiry not found"));
        inquiry.changeStatus(status, clock.instant());
    }
}
