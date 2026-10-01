package com.mandao.pricing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tarifa versionada. Nunca se edita: cada cambio crea una versión nueva (AC-010-05). */
@Entity
@Table(name = "tariffs")
public class Tariff {

    @Id
    private UUID id;
    private int version;
    private long baseFare;
    private long perKm;
    private int includedWaitMin;
    private long perExtraMin;
    private long perExtraStop;
    private long complexityMedium;
    private long complexityComplex;
    private BigDecimal commissionPct;
    private boolean active;
    private UUID createdBy;
    private Instant createdAt;

    protected Tariff() {
    }

    public Tariff(int version, long baseFare, long perKm, int includedWaitMin, long perExtraMin, long perExtraStop,
                  long complexityMedium, long complexityComplex, BigDecimal commissionPct, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.version = version;
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.includedWaitMin = includedWaitMin;
        this.perExtraMin = perExtraMin;
        this.perExtraStop = perExtraStop;
        this.complexityMedium = complexityMedium;
        this.complexityComplex = complexityComplex;
        this.commissionPct = commissionPct;
        this.active = true;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public long complexitySurcharge(Complexity complexity) {
        return switch (complexity) {
            case SENCILLA -> 0;
            case MEDIA -> complexityMedium;
            case COMPLEJA -> complexityComplex;
        };
    }

    public void deactivate() {
        this.active = false;
    }

    public int getVersion() {
        return version;
    }

    public long getBaseFare() {
        return baseFare;
    }

    public long getPerKm() {
        return perKm;
    }

    public int getIncludedWaitMin() {
        return includedWaitMin;
    }

    public long getPerExtraMin() {
        return perExtraMin;
    }

    public long getPerExtraStop() {
        return perExtraStop;
    }

    public long getComplexityMedium() {
        return complexityMedium;
    }

    public long getComplexityComplex() {
        return complexityComplex;
    }

    public BigDecimal getCommissionPct() {
        return commissionPct;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
