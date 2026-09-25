package com.ssolab.gallery.api;

import com.ssolab.gallery.artwork.StudioArtworkService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(GalleryRoutes.STUDIO_API + "/artworks")
public class StudioGalleryController {
    private final StudioArtworkService service;
    public StudioGalleryController(StudioArtworkService service) { this.service = service; }

    @GetMapping List<GalleryDtos.Artwork> list() { return service.list(); }
    @GetMapping("/{id}") GalleryDtos.Artwork get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    GalleryDtos.Artwork create(@Valid @RequestBody GalleryDtos.ArtworkUpsert request) {
        return service.create(request);
    }
    @PatchMapping("/{id}")
    GalleryDtos.Artwork update(@PathVariable UUID id,
        @Valid @RequestBody GalleryDtos.ArtworkUpsert request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { service.delete(id); }
    @PatchMapping("/order") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorder(@Valid @RequestBody GalleryDtos.ReorderRequest request) { service.reorder(request); }
    @PostMapping(path = "/{id}/images", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    GalleryDtos.Image upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file)
        throws IOException { return service.upload(id, file); }
    @PatchMapping("/{id}/images/primary") @ResponseStatus(HttpStatus.NO_CONTENT)
    void primary(@PathVariable UUID id,
        @Valid @RequestBody GalleryDtos.PrimaryImageRequest request) {
        service.makePrimary(id, request.imageId());
    }
    @DeleteMapping("/{id}/images/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) throws IOException {
        service.deleteImage(id, imageId);
    }
}
