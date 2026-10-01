package com.mandao.ratings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ratings")
public class Rating {

    public enum Tag { CUIDADOSO, RAPIDO, AMABLE, BUENA_COMUNICACION, EXCELENTE_DILIGENCIAS }

    @Id
    private UUID id;
    private UUID errandId;
    private UUID raterId;
    private UUID courierId;
    private short stars;
    private String tags;
    private String comment;
    private Instant createdAt;

    protected Rating() {
    }

    public Rating(UUID errandId, UUID raterId, UUID courierId, int stars, String tags, String comment) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.raterId = raterId;
        this.courierId = courierId;
        this.stars = (short) stars;
        this.tags = tags;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public UUID getErrandId() {
        return errandId;
    }

    public int getStars() {
        return stars;
    }

    public String getTags() {
        return tags;
    }

    public String getComment() {
        return comment;
    }
}
