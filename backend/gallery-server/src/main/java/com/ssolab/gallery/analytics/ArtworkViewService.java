package com.ssolab.gallery.analytics;

import com.ssolab.gallery.artwork.ArtworkRepository;
import com.ssolab.gallery.config.GalleryAnalyticsProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtworkViewService {
    public static final String VISITOR_COOKIE = "qwg_visitor";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern BOT = Pattern.compile(
        "bot|crawler|spider|slurp|bingpreview|facebookexternalhit|whatsapp|kakaotalk-scrap|headless|monitoring|uptime",
        Pattern.CASE_INSENSITIVE);

    private final ArtworkRepository artworks;
    private final JdbcTemplate jdbc;
    private final GalleryAnalyticsProperties properties;
    private final Clock clock;

    @Autowired
    public ArtworkViewService(ArtworkRepository artworks, JdbcTemplate jdbc,
        GalleryAnalyticsProperties properties) {
        this(artworks, jdbc, properties, Clock.systemUTC());
    }

    ArtworkViewService(ArtworkRepository artworks, JdbcTemplate jdbc,
        GalleryAnalyticsProperties properties, Clock clock) {
        this.artworks = artworks;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isBot(String userAgent) {
        return userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find();
    }

    public String newVisitorToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public int cookieMaxAgeSeconds() {
        return Math.multiplyExact(properties.cookieMaxAgeDays(), 24 * 60 * 60);
    }

    @Transactional
    public boolean record(UUID publicId, ArtworkAnalyticsDtos.ViewRequest request,
        String visitorToken, String referrer, String currentHost) {
        var artwork = artworks.findPublishedByPublicId(publicId)
            .orElse(null);
        if (artwork == null) return false;

        Instant now = clock.instant();
        // PostgreSQL JDBC supports OffsetDateTime for TIMESTAMPTZ, not Instant.
        var timestamp = now.atOffset(ZoneOffset.UTC);
        LocalDate viewedDate = now.atZone(SEOUL).toLocalDate();
        String visitorHash = hash(visitorToken);
        String referrerHost = referrerHost(referrer);
        ArtworkEntrySource entrySource = effectiveEntrySource(request.entrySource(),
            referrerHost, currentHost);
        UUID eventId = UUID.randomUUID();
        String sql = """
            WITH accepted AS (
                INSERT INTO gallery.artwork_view_dedup
                    (artwork_id, visitor_hash, surface, last_counted_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (artwork_id, visitor_hash, surface)
                DO UPDATE SET last_counted_at = EXCLUDED.last_counted_at
                WHERE gallery.artwork_view_dedup.last_counted_at
                    <= EXCLUDED.last_counted_at - (? * INTERVAL '1 minute')
                RETURNING artwork_id
            )
            INSERT INTO gallery.artwork_view_events
                (id, artwork_id, visitor_hash, surface, entry_source, referrer_host,
                 viewed_at, viewed_date)
            SELECT ?, artwork_id, ?, ?, ?, ?, ?, ?
            FROM accepted
            """;
        return jdbc.update(sql, artwork.getId(), visitorHash, request.surface().name(), timestamp,
            properties.dedupMinutes(), eventId, visitorHash, request.surface().name(),
            entrySource.name(), referrerHost, timestamp, viewedDate) == 1;
    }

    private String hash(String visitorToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(visitorToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot hash gallery visitor token", exception);
        }
    }

    private String referrerHost(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String host = URI.create(value).getHost();
            if (host == null) return null;
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.length() > 253 ? normalized.substring(0, 253) : normalized;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ArtworkEntrySource effectiveEntrySource(ArtworkEntrySource requested,
        String referrerHost, String currentHost) {
        if (requested != ArtworkEntrySource.DIRECT || referrerHost == null) return requested;
        return referrerHost.equalsIgnoreCase(currentHost)
            ? ArtworkEntrySource.INTERNAL : ArtworkEntrySource.EXTERNAL;
    }
}
