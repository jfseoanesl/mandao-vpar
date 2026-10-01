package com.mandao.payments;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Type { HOLD, PURCHASE, REFUND, COURIER_PAYOUT, PLATFORM_FEE }

    @Id
    private UUID id;
    private UUID errandId;
    @Enumerated(EnumType.STRING)
    private Type type;
    private long amount;
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID errandId, Type type, long amount) {
        this.id = UUID.randomUUID();
        this.errandId = errandId;
        this.type = type;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public UUID getErrandId() {
        return errandId;
    }

    public Type getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }
}
