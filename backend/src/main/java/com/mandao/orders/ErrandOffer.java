package com.mandao.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Oferta de un mandadero en un mandado negociable (spec 003). */
@Entity
@Table(name = "errand_offers")
public class ErrandOffer {

    public enum Status { PENDING, COUNTERED, ACCEPTED, REJECTED }

    @Id
    private UUID id;
    private UUID errandId;
    private UUID courierId;
    private long amount;
    private String message;
    private Long counterAmount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;

    protected ErrandOffer() {
    }

    public ErrandOffer(UUID errandId, UUID courierId, long amount, String message) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.courierId = courierId;
        this.amount = amount;
        this.message = message;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void counter(long counterAmount) {
        this.counterAmount = counterAmount;
        this.status = Status.COUNTERED;
        this.updatedAt = Instant.now();
    }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public boolean isLive() {
        return status == Status.PENDING || status == Status.COUNTERED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getErrandId() {
        return errandId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public long getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }

    public Long getCounterAmount() {
        return counterAmount;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
