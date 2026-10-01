package com.mandao.orders;

import com.mandao.analytics.AnalyticsService;
import com.mandao.payments.SettlementService;
import com.mandao.notifications.PushMessages;
import com.mandao.notifications.PushService;
import com.mandao.tracking.RealtimePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Único punto de cambio de estado: valida la transición, sella tiempos, guarda historial,
 * dispara efectos (liquidación, analítica) y notifica en tiempo real (AC-006-05).
 */
@Component
public class ErrandLifecycle {

    private final ErrandRepository errands;
    private final ErrandStatusHistoryRepository history;
    private final SettlementService settlement;
    private final AnalyticsService analytics;
    private final RealtimePublisher realtime;
    private final PushService push;

    public ErrandLifecycle(ErrandRepository errands, ErrandStatusHistoryRepository history,
                           SettlementService settlement, AnalyticsService analytics, RealtimePublisher realtime,
                           PushService push) {
        this.errands = errands;
        this.history = history;
        this.settlement = settlement;
        this.analytics = analytics;
        this.realtime = realtime;
        this.push = push;
    }

    /** Registra el estado inicial de un mandado recién creado. */
    @Transactional
    public void created(Errand errand, UUID actorId) {
        history.save(new ErrandStatusHistory(errand.getId(), null, errand.getStatus(), actorId, null));
        realtime.admin("ERRAND_CREATED", errand.getId(), Map.of("code", errand.getCode(),
                "status", errand.getStatus().name()));
    }

    @Transactional
    public Errand transition(Errand errand, ErrandStatus to, UUID actorId, String note) {
        ErrandStatus from = errand.getStatus();
        ErrandStateMachine.check(from, to);
        errand.applyStatus(to);
        Errand saved = errands.saveAndFlush(errand);
        history.save(new ErrandStatusHistory(errand.getId(), from, to, actorId, note));

        switch (to) {
            case ASSIGNED -> analytics.track("errand_assigned", errand.getCourierId(), errand.getId(), null, null);
            case COMPLETED -> {
                settlement.settle(errand);
                analytics.track("errand_completed", errand.getClientId(), errand.getId(),
                        String.valueOf(errand.getTotalPrice()), null);
            }
            case CANCELLED -> {
                settlement.onCancelled(errand);
                analytics.track("errand_cancelled", actorId, errand.getId(), from.name(), null);
            }
            case FAILED -> analytics.track("errand_failed", actorId, errand.getId(), from.name(), null);
            default -> {
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("from", from.name());
        if (note != null) {
            data.put("note", note);
        }
        realtime.errand(errand.getId(), "STATUS_CHANGED", to.name(), data);
        realtime.admin("STATUS_CHANGED", errand.getId(), Map.of("code", errand.getCode(), "status", to.name()));
        if (errand.getCourierId() != null && (to == ErrandStatus.CANCELLED || to == ErrandStatus.ASSIGNED)) {
            realtime.courier(errand.getCourierId(), "ERRAND_" + to.name(), errand.getId(), Map.of());
        }
        // Spec 012: el cliente se entera sin tener la app abierta; el mandadero, si le cancelan.
        PushService.Message forClient = PushMessages.forClient(errand, to);
        if (forClient != null && !errand.getClientId().equals(actorId)) {
            push.notifyUser(errand.getClientId(), forClient);
        }
        if (to == ErrandStatus.CANCELLED && errand.getCourierId() != null && !errand.getCourierId().equals(actorId)) {
            push.notifyUser(errand.getCourierId(), PushMessages.courierCancelled(errand));
        }
        return saved;
    }
}
