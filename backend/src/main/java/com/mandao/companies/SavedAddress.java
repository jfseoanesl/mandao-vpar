package com.mandao.companies;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_addresses")
public class SavedAddress {

    @Id
    private UUID id;
    private UUID companyId;
    private String label;
    private String address;
    private double lat;
    private double lng;
    private Instant createdAt;

    protected SavedAddress() {
    }

    public SavedAddress(UUID companyId, String label, String address, double lat, double lng) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.label = label;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getLabel() {
        return label;
    }

    public String getAddress() {
        return address;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
