package com.mandao.matching;

import com.mandao.couriers.CourierProfileRepository;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandRepository;
import com.mandao.orders.ErrandStatus;
import com.mandao.orders.ErrandStop;
import com.mandao.identity.Role;
import com.mandao.notifications.PushMessages;
import com.mandao.notifications.PushService;
import com.mandao.tracking.RealtimePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Despachador (spec 004): cada 10 s ofrece los mandados abiertos a los mandaderos más cercanos
 * con radio incremental y alerta al operador a los 5 minutos sin asignar.
 */
@Service
public class DispatchService {

    static final int MAX_CANDIDATES = 5;
    static final Duration FRESH_PING = Duration.ofMinutes(2);
    static final Duration ATTENTION_AFTER = Duration.ofMinutes(5);
    static final Duration SCHEDULE_LEAD = Duration.ofMinutes(15);

    private final ErrandRepository errands;
    private final CourierProfileRepository couriers;
    private final RealtimePublisher realtime;
    private final PushService push;
    /** Mandaderos ya notificados por mandado (memoria de una instancia, suficiente para el MVP). */
    private final Map<UUID, Set<UUID>> notified = new ConcurrentHashMap<>();

    public DispatchService(ErrandRepository errands, CourierProfileRepository couriers, RealtimePublisher realtime,
                           PushService push) {
        this.errands = errands;
        this.couriers = couriers;
        this.realtime = realtime;
        this.push = push;
    }

    /** Radio en km según el tiempo esperando: 2 km (0–60 s), 4 km (60–120 s), 8 km después. */
    static double radiusKm(Duration waiting) {
        long s = waiting.toSeconds();
        return s < 60 ? 2 : s < 120 ? 4 : 8;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    @Transactional
    public void dispatch() {
        Instant now = Instant.now();
        List<Errand> open = errands.findByStatusIn(ErrandStatus.OPEN);
        notified.keySet().retainAll(open.stream().map(Errand::getId).toList());
        for (Errand e : open) {
            if (e.getScheduledFor() != null && e.getScheduledFor().isAfter(now.plus(SCHEDULE_LEAD))) {
                continue;
            }
            Instant readyAt = e.getScheduledFor() != null && e.getScheduledFor().minus(SCHEDULE_LEAD)
                    .isAfter(e.getCreatedAt()) ? e.getScheduledFor().minus(SCHEDULE_LEAD) : e.getCreatedAt();
            Duration waiting = Duration.between(readyAt, now);
            offer(e, waiting, now);
            flagIfStale(e, waiting);
        }
    }

    void offer(Errand e, Duration waiting, Instant now) {
        ErrandStop origin = e.firstStop();
        List<UUID> candidates = couriers.findNearbyCandidates(origin.getLat(), origin.getLng(),
                radiusKm(waiting) * 1000, now.minus(FRESH_PING), MAX_CANDIDATES);
        Set<UUID> already = notified.computeIfAbsent(e.getId(), k -> ConcurrentHashMap.newKeySet());
        for (UUID courierId : candidates) {
            if (already.add(courierId)) {
                realtime.courier(courierId, "NEW_OPPORTUNITY", e.getId(), Map.of(
                        "code", e.getCode(),
                        "category", e.getCategory().name(),
                        "payout", e.getCourierPayout(),
                        "negotiable", e.getStatus() == ErrandStatus.NEGOTIATING));
                push.notifyUser(courierId, PushMessages.newOpportunity(e));
            }
        }
    }

    /** AC-004-07. */
    void flagIfStale(Errand e, Duration waiting) {
        if (!e.isNeedsAttention() && waiting.compareTo(ATTENTION_AFTER) >= 0) {
            e.flagAttention("Sin mandadero tras " + waiting.toMinutes() + " min: asignar manualmente");
            errands.save(e);
            realtime.admin("NEEDS_ATTENTION", e.getId(), Map.of("code", e.getCode(),
                    "reason", e.getAttentionReason()));
            push.notifyRole(Role.ADMIN, PushMessages.needsAttention(e));
        }
    }
}
