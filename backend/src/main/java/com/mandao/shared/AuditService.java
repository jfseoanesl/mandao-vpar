package com.mandao.shared;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(UUID actorId, String action, String entity, Object entityId, String detail) {
        repository.save(new AuditLog(actorId, action, entity, entityId == null ? null : entityId.toString(), detail));
    }
}
