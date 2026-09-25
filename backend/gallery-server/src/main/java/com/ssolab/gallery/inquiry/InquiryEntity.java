package com.ssolab.gallery.inquiry;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inquiries", schema = "gallery")
public class InquiryEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artwork_id", nullable = false)
    private ArtworkEntity artwork;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 3000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private InquiryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InquiryEntity() { }

    public UUID getId() { return id; }
    public ArtworkEntity getArtwork() { return artwork; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public InquiryStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
