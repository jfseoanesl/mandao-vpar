package com.mandao.tracking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_pings")
public class LocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID courierId;
    private UUID errandId;
    private double lat;
    private double lng;
    private Double accuracy;
    private Instant createdAt;

    protected LocationPing() {
    }

    public LocationPing(UUID courierId, UUID errandId, double lat, double lng, Double accuracy) {
        this.courierId = courierId;
        this.errandId = errandId;
        this.lat = lat;
        this.lng = lng;
        this.accuracy = accuracy;
        this.createdAt = Instant.now();
    }

    public UUID getErrandId() {
        return errandId;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
