package com.ssolab.gallery.api;

import com.ssolab.gallery.inquiry.InquiryRateLimiter;
import com.ssolab.gallery.inquiry.InquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InquiryController {
    private final InquiryService service;
    private final InquiryRateLimiter limiter;
    public InquiryController(InquiryService service, InquiryRateLimiter limiter) {
        this.service = service; this.limiter = limiter;
    }
    @PostMapping(GalleryRoutes.PUBLIC_API + "/inquiries") @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> create(@Valid @RequestBody GalleryDtos.InquiryCreate request,
        HttpServletRequest servletRequest) {
        limiter.check(servletRequest.getRemoteAddr());
        return Map.of("id", service.create(request));
    }
    @GetMapping(GalleryRoutes.STUDIO_API + "/inquiries")
    GalleryDtos.Page<GalleryDtos.Inquiry> list(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "30") int size) { return service.list(page, size); }
    @PatchMapping(GalleryRoutes.STUDIO_API + "/inquiries/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void status(@org.springframework.web.bind.annotation.PathVariable UUID id,
        @Valid @RequestBody GalleryDtos.InquiryStatusUpdate request) { service.status(id, request.status()); }
}
