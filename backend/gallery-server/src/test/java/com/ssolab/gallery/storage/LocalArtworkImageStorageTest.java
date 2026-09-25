package com.ssolab.gallery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssolab.gallery.api.GalleryValidationException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalArtworkImageStorageTest {
    private Path root;

    @BeforeEach
    void createWorkspaceTemp() throws Exception {
        root = Path.of("build", "tmp", "gallery-storage-test", java.util.UUID.randomUUID().toString());
        Files.createDirectories(root);
    }

    @AfterEach
    void removeWorkspaceTemp() throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void storesOriginalWebAndThumbnailVariants() throws Exception {
        LocalArtworkImageStorage storage = new LocalArtworkImageStorage(
            new GalleryStorageProperties(root, 1_000_000, 1200, 200));
        StoredArtworkImage stored = storage.store(new MockMultipartFile("file", "work.png",
            "image/png", png()));

        assertThat(Files.isRegularFile(storage.resolve(stored.originalPath()))).isTrue();
        assertThat(Files.isRegularFile(storage.resolve(stored.webImagePath()))).isTrue();
        assertThat(Files.isRegularFile(storage.resolve(stored.thumbnailPath()))).isTrue();
        assertThat(stored.storageKey()).matches("[a-f0-9-]{36}");
    }

    @Test
    void rejectsSvgBeforeWritingAnything() throws Exception {
        LocalArtworkImageStorage storage = new LocalArtworkImageStorage(
            new GalleryStorageProperties(root, 1_000_000, 1200, 200));
        var file = new MockMultipartFile("file", "unsafe.svg", "image/svg+xml",
            "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> storage.store(file)).isInstanceOf(GalleryValidationException.class);
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 640, 480); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output); return output.toByteArray();
    }
}
