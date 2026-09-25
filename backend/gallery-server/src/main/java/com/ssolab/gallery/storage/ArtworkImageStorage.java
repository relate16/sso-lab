package com.ssolab.gallery.storage;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface ArtworkImageStorage {
    StoredArtworkImage store(MultipartFile file) throws IOException;
    Path resolve(String relativePath);
    void delete(StoredArtworkImage image) throws IOException;
}
