package com.ssolab.gallery.analytics;

import com.ssolab.gallery.artwork.ArtworkEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read mapping only. Event recording remains an atomic PostgreSQL operation. */
@Entity
@Immutable
@Table(name = "artwork_view_events", schema = "gallery")
public class ArtworkViewEventEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private ArtworkEntity artwork;

    @Column(name = "visitor_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String visitorHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArtworkViewSurface surface;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_source", nullable = false, length = 32)
    private ArtworkEntrySource entrySource;

    @Column(name = "referrer_host", length = 253)
    private String referrerHost;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    @Column(name = "viewed_date", nullable = false)
    private LocalDate viewedDate;

    // Hibernate maps InetAddress to PostgreSQL INET (binary fallback for H2).
    @Column(name = "visitor_ip")
    private InetAddress visitorIp;

    protected ArtworkViewEventEntity() { }
}
