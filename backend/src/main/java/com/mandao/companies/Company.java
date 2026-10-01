package com.mandao.companies;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    /** Planes hipotéticos de Mandao.md; el cobro en el piloto es manual. */
    public enum Plan {
        BASICO(0), PYME(79_000), EMPRESAS(99_000), PRO(199_000);

        private final long monthlyFee;

        Plan(long monthlyFee) {
            this.monthlyFee = monthlyFee;
        }

        public long monthlyFee() {
            return monthlyFee;
        }
    }

    @Id
    private UUID id;
    private String name;
    private String nit;
    @Enumerated(EnumType.STRING)
    private Plan plan;
    private long monthlyFee;
    private boolean active;
    private Instant createdAt;

    protected Company() {
    }

    public Company(String name, String nit, Plan plan) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.nit = nit;
        this.plan = plan;
        this.monthlyFee = plan.monthlyFee();
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNit() {
        return nit;
    }

    public Plan getPlan() {
        return plan;
    }

    public long getMonthlyFee() {
        return monthlyFee;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
