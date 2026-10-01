package com.mandao.couriers;

import com.mandao.couriers.CourierEnums.DocumentType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_documents")
public class CourierDocument {

    @Id
    private UUID id;
    private UUID courierId;
    @Enumerated(EnumType.STRING)
    private DocumentType type;
    private String objectKey;
    private String contentType;
    private Instant uploadedAt;

    protected CourierDocument() {
    }

    public CourierDocument(UUID courierId, DocumentType type) {
        this.id = UUID.randomUUID();
        this.courierId = courierId;
        this.type = type;
    }

    public void replaceFile(String objectKey, String contentType) {
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
