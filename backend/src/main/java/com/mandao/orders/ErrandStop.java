package com.mandao.orders;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "errand_stops")
public class ErrandStop {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "errand_id")
    private Errand errand;
    private int seq;
    private String address;
    private double lat;
    private double lng;
    private String instructions;
    private String contactName;
    private String contactPhone;

    protected ErrandStop() {
    }

    public ErrandStop(Errand errand, int seq, String address, double lat, double lng, String instructions,
                      String contactName, String contactPhone) {
        this.id = UUID.randomUUID();
        this.errand = errand;
        this.seq = seq;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.instructions = instructions;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
    }

    public UUID getId() {
        return id;
    }

    public int getSeq() {
        return seq;
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

    public String getInstructions() {
        return instructions;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }
}
