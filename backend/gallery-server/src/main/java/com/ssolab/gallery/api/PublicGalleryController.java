package com.ssolab.gallery.api;

import com.ssolab.gallery.artwork.PublicArtworkService;
import com.ssolab.gallery.artwork.SaleStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GalleryRoutes.PUBLIC_API)
public class PublicGalleryController {
    private final PublicArtworkService service;
    public PublicGalleryController(PublicArtworkService service) { this.service = service; }

    @GetMapping("/home")
    List<GalleryDtos.Artwork> home() { return service.home(); }

    @GetMapping("/artworks")
    GalleryDtos.Page<GalleryDtos.Artwork> artworks(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) SaleStatus saleStatus,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String size,
        @RequestParam(defaultValue = "recent") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "24") int pageSize) {
        return service.search(q, saleStatus, minPrice, maxPrice, size, sort, page, pageSize);
    }

    @GetMapping("/artworks/{publicId}")
    GalleryDtos.Artwork artwork(@PathVariable UUID publicId) { return service.detail(publicId); }
}
