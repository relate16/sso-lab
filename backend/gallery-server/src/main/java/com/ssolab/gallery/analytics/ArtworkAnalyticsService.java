package com.ssolab.gallery.analytics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtworkAnalyticsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public ArtworkAnalyticsService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ArtworkAnalyticsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ArtworkAnalyticsDtos.Overview overview() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        var lifetime = metric(null);
        var todayMetric = metric(today);
        var last7Days = metric(today.minusDays(6));
        var last30Days = metric(today.minusDays(29));
        return new ArtworkAnalyticsDtos.Overview(lifetime, todayMetric, last7Days,
            last30Days, daily(today.minusDays(29)), topArtworks(), breakdown("surface"),
            breakdown("entry_source"), referrers());
    }

    private ArtworkAnalyticsDtos.Metric metric(LocalDate from) {
        String where = from == null ? "" : " WHERE viewed_date >= ?";
        Object[] arguments = from == null ? new Object[0] : new Object[] { from };
        return jdbc.queryForObject("SELECT COUNT(*) AS views, COUNT(DISTINCT visitor_hash) AS visitors "
            + "FROM gallery.artwork_view_events" + where, (result, row) ->
                new ArtworkAnalyticsDtos.Metric(result.getLong("views"),
                    result.getLong("visitors")), arguments);
    }

    private List<ArtworkAnalyticsDtos.Daily> daily(LocalDate from) {
        return jdbc.query("""
            SELECT viewed_date, COUNT(*) AS views, COUNT(DISTINCT visitor_hash) AS visitors
            FROM gallery.artwork_view_events
            WHERE viewed_date >= ?
            GROUP BY viewed_date
            ORDER BY viewed_date
            """, (result, row) -> new ArtworkAnalyticsDtos.Daily(
                result.getObject("viewed_date", LocalDate.class), result.getLong("views"),
                result.getLong("visitors")), from);
    }

    private List<ArtworkAnalyticsDtos.TopArtwork> topArtworks() {
        return jdbc.query("""
            SELECT a.public_id, a.title, a.archived_at IS NOT NULL AS archived,
                   COUNT(*) AS views, COUNT(DISTINCT e.visitor_hash) AS visitors
            FROM gallery.artwork_view_events e
            JOIN gallery.artworks a ON a.id = e.artwork_id
            GROUP BY a.id, a.public_id, a.title, a.archived_at
            ORDER BY views DESC, visitors DESC, a.title
            LIMIT 20
            """, (result, row) -> new ArtworkAnalyticsDtos.TopArtwork(
                result.getObject("public_id", UUID.class), result.getString("title"),
                result.getBoolean("archived"), result.getLong("views"),
                result.getLong("visitors")));
    }

    private List<ArtworkAnalyticsDtos.Breakdown> breakdown(String column) {
        if (!column.equals("surface") && !column.equals("entry_source")) {
            throw new IllegalArgumentException("invalid analytics breakdown");
        }
        return jdbc.query("SELECT " + column + " AS key, COUNT(*) AS views, "
            + "COUNT(DISTINCT visitor_hash) AS visitors "
            + "FROM gallery.artwork_view_events GROUP BY " + column + " ORDER BY views DESC",
            this::breakdownRow);
    }

    private List<ArtworkAnalyticsDtos.Breakdown> referrers() {
        return jdbc.query("""
            SELECT COALESCE(referrer_host, '직접 접속') AS key,
                   COUNT(*) AS views, COUNT(DISTINCT visitor_hash) AS visitors
            FROM gallery.artwork_view_events
            GROUP BY COALESCE(referrer_host, '직접 접속')
            ORDER BY views DESC
            LIMIT 20
            """, this::breakdownRow);
    }

    private ArtworkAnalyticsDtos.Breakdown breakdownRow(ResultSet result, int row)
        throws SQLException {
        return new ArtworkAnalyticsDtos.Breakdown(result.getString("key"),
            result.getLong("views"), result.getLong("visitors"));
    }
}
