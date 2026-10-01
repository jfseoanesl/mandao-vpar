package com.mandao.evidence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidences")
public class Evidence {

    public enum Type { FOTO, FACTURA, FIRMA, NOTA, ADJUNTO }

    @Id
    private UUID id;
    private UUID errandId;
    private UUID uploaderId;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String objectKey;
    private String contentType;
    private String note;
    private Long amount;
    private Double lat;
    private Double lng;
    private Instant createdAt;

    protected Evidence() {
    }

    public Evidence(UUID errandId, UUID uploaderId, Type type, String objectKey, String contentType, String note,
                    Long amount, Double lat, Double lng) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.uploaderId = uploaderId;
        this.type = type;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.note = note;
        this.amount = amount;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getErrandId() {
        return errandId;
    }

    public UUID getUploaderId() {
        return uploaderId;
    }

    public Type getType() {
        return type;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getNote() {
        return note;
    }

    public Long getAmount() {
        return amount;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLng() {
        return lng;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
