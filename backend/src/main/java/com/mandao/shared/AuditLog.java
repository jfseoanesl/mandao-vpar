package com.mandao.shared;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID actorId;
    private String action;
    private String entity;
    private String entityId;
    private String detail;
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID actorId, String action, String entity, String entityId, String detail) {
        this.actorId = actorId;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.detail = detail == null ? null : detail.substring(0, Math.min(500, detail.length()));
        this.createdAt = Instant.now();
    }

    public String getAction() {
        return action;
    }

    public String getEntityId() {
        return entityId;
    }
}
