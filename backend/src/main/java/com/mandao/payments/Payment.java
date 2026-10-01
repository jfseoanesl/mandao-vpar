package com.mandao.payments;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { PENDING, CONFIRMED, VOIDED }

    @Id
    private UUID id;
    private UUID errandId;
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;
    private long amount;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String reference;
    private UUID confirmedBy;
    private Instant confirmedAt;
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(UUID errandId, PaymentMethod method, long amount) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.method = method;
        this.amount = amount;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void confirm(UUID adminId, String reference) {
        this.status = Status.CONFIRMED;
        this.confirmedBy = adminId;
        this.reference = reference;
        this.confirmedAt = Instant.now();
    }

    public void voidPayment() {
        this.status = Status.VOIDED;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getErrandId() {
        return errandId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public long getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
