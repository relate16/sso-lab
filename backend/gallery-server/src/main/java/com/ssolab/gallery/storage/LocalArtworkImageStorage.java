package com.ssolab.gallery.storage;

import com.ssolab.gallery.api.GalleryValidationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalArtworkImageStorage implements ArtworkImageStorage {
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final GalleryStorageProperties properties;
    private final Path root;

    public LocalArtworkImageStorage(GalleryStorageProperties properties) throws IOException {
        this.properties = properties;
        this.root = properties.root().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredArtworkImage store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new GalleryValidationException("image is required");
        if (file.getSize() > properties.maxUploadBytes()) throw new GalleryValidationException("image is too large");
        String declaredType = file.getContentType();
        if (!CONTENT_TYPES.contains(declaredType)) throw new GalleryValidationException("unsupported image type");
        BufferedImage decoded;
        try (InputStream input = file.getInputStream()) { decoded = ImageIO.read(input); }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
            throw new GalleryValidationException("invalid image data");
        }
        long pixels = (long) decoded.getWidth() * decoded.getHeight();
        if (pixels > 50_000_000L) throw new GalleryValidationException("image dimensions are too large");

        String key = UUID.randomUUID().toString();
        Path directory = resolve(key);
        Files.createDirectories(directory);
        String originalExtension = switch (declaredType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        Path original = directory.resolve("original" + originalExtension);
        Path web = directory.resolve("web.jpg");
        Path thumbnail = directory.resolve("thumbnail.jpg");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, original, StandardCopyOption.REPLACE_EXISTING);
            }
            writeJpeg(decoded, web, properties.webMaxPixels(), .9f);
            writeJpeg(decoded, thumbnail, properties.thumbnailMaxPixels(), .82f);
            return new StoredArtworkImage(key, relative(original), relative(web), relative(thumbnail),
                declaredType, decoded.getWidth(), decoded.getHeight());
        } catch (RuntimeException | IOException exception) {
            deleteTree(directory);
            throw exception;
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) throw new GalleryValidationException("invalid storage path");
        return resolved;
    }

    @Override
    public void delete(StoredArtworkImage image) throws IOException {
        deleteTree(resolve(image.storageKey()));
    }

    private void writeJpeg(BufferedImage source, Path target, int maximum, float quality) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        Thumbnails.of(rgb).size(maximum, maximum).outputFormat("jpg").outputQuality(quality).toFile(target.toFile());
    }

    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    private void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
