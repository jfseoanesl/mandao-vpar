package com.mandao.tracking;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos por WebSocket. Si hay transacción activa, espera al commit
 * para no anunciar cambios que luego se revierten.
 */
@Component
public class RealtimePublisher {

    public record RealtimeEvent(String type, UUID errandId, String status, Map<String, Object> data, Instant at) {
    }

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void errand(UUID errandId, String type, String status, Map<String, Object> data) {
        RealtimeEvent event = new RealtimeEvent(type, errandId, status, data, Instant.now());
        afterCommit(() -> template.convertAndSend("/topic/errands/" + errandId, event));
    }

    public void courier(UUID courierId, String type, UUID errandId, Map<String, Object> data) {
        RealtimeEvent event = new RealtimeEvent(type, errandId, null, data, Instant.now());
        afterCommit(() -> template.convertAndSendToUser(courierId.toString(), "/queue/offers", event));
    }

    public void admin(String type, UUID errandId, Map<String, Object> data) {
        RealtimeEvent event = new RealtimeEvent(type, errandId, null, data, Instant.now());
        afterCommit(() -> template.convertAndSend("/topic/admin", event));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
