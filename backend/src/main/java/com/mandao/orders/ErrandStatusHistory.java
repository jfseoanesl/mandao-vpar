package com.mandao.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "errand_status_history")
public class ErrandStatusHistory {

    @Id
    private UUID id;
    private UUID errandId;
    @Enumerated(EnumType.STRING)
    private ErrandStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ErrandStatus toStatus;
    private UUID actorId;
    private String note;
    private Instant createdAt;

    protected ErrandStatusHistory() {
    }

    public ErrandStatusHistory(UUID errandId, ErrandStatus fromStatus, ErrandStatus toStatus, UUID actorId,
                               String note) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.note = note == null ? null : note.substring(0, Math.min(300, note.length()));
        this.createdAt = Instant.now();
    }

    public ErrandStatus getFromStatus() {
        return fromStatus;
    }

    public ErrandStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
