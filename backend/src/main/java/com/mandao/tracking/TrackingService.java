package com.mandao.tracking;

import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandAccess;
import com.mandao.orders.ErrandRepository;
import com.mandao.orders.ErrandStatus;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Ubicación en tiempo real (spec 006). */
@Service
public class TrackingService {

    public record LivePoint(double lat, double lng, Instant at) {
    }

    public record LiveCourier(UUID id, String fullName, double lat, double lng, boolean available, Instant lastPingAt,
                              String activeErrandCode, UUID activeErrandId) {
    }

    public record LiveErrand(UUID id, String code, ErrandStatus status, double lat, double lng, UUID courierId,
                             boolean needsAttention) {
    }

    public record LiveMap(List<LiveCourier> couriers, List<LiveErrand> errands) {
    }

    static final Duration PING_RETENTION = Duration.ofDays(30);
    static final Duration LIVE_WINDOW = Duration.ofMinutes(10);

    private final CourierProfileRepository couriers;
    private final ErrandRepository errands;
    private final LocationPingRepository pings;
    private final UserRepository users;
    private final ErrandAccess access;
    private final RealtimePublisher realtime;

    public TrackingService(CourierProfileRepository couriers, ErrandRepository errands, LocationPingRepository pings,
                           UserRepository users, ErrandAccess access, RealtimePublisher realtime) {
        this.couriers = couriers;
        this.errands = errands;
        this.pings = pings;
        this.users = users;
        this.access = access;
        this.realtime = realtime;
    }

    /** AC-006-01 / AC-006-02. */
    @Transactional
    public void ping(UUID courierId, double lat, double lng, Double accuracy) {
        CourierProfile profile = couriers.findById(courierId).orElseThrow(() -> ApiException.notFound("Mandadero"));
        Errand active = errands.findFirstByCourierIdAndStatusIn(courierId, ErrandStatus.COURIER_ACTIVE).orElse(null);
        if (!profile.isAvailable() && active == null) {
            throw ApiException.conflict("Activa tu disponibilidad para compartir ubicación");
        }
        profile.updateLocation(lat, lng);
        Map<String, Object> point = Map.of("lat", lat, "lng", lng);
        if (active != null) {
            pings.save(new LocationPing(courierId, active.getId(), lat, lng, accuracy));
            realtime.errand(active.getId(), "LOCATION", active.getStatus().name(), point);
        }
        realtime.admin("COURIER_LOCATION", active == null ? null : active.getId(),
                Map.of("courierId", courierId.toString(), "lat", lat, "lng", lng,
                        "available", profile.isAvailable()));
    }

    /** Recorrido del mandadero durante el servicio, para dibujar la ruta. */
    @Transactional(readOnly = true)
    public List<LivePoint> track(UUID errandId, AuthUser user) {
        ErrandAccess.Access a = access.resolve(errandId, user, false);
        List<LivePoint> points = new ArrayList<>(pings.findTop300ByErrandIdOrderByCreatedAtDesc(a.errand().getId())
                .stream().map(p -> new LivePoint(p.getLat(), p.getLng(), p.getCreatedAt())).toList());
        java.util.Collections.reverse(points);
        return points;
    }

    /** AC-010-06. */
    @Transactional(readOnly = true)
    public LiveMap liveMap() {
        Instant since = Instant.now().minus(LIVE_WINDOW);
        List<Errand> active = errands.findByStatusIn(List.of(ErrandStatus.SEARCHING, ErrandStatus.NEGOTIATING,
                ErrandStatus.ASSIGNED, ErrandStatus.GOING_TO_ORIGIN, ErrandStatus.IN_PROGRESS,
                ErrandStatus.DELIVERED));
        Map<UUID, Errand> byCourier = active.stream().filter(e -> e.getCourierId() != null)
                .collect(Collectors.toMap(Errand::getCourierId, Function.identity(), (a, b) -> a));
        List<CourierProfile> live = couriers.findAll().stream()
                .filter(p -> p.getLat() != null && p.getLastPingAt() != null && p.getLastPingAt().isAfter(since))
                .toList();
        Map<UUID, User> names = users.findAllById(live.stream().map(CourierProfile::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<LiveCourier> courierViews = live.stream().map(p -> {
            Errand e = byCourier.get(p.getUserId());
            User u = names.get(p.getUserId());
            return new LiveCourier(p.getUserId(), u == null ? null : u.getFullName(), p.getLat(), p.getLng(),
                    p.isAvailable(), p.getLastPingAt(), e == null ? null : e.getCode(), e == null ? null : e.getId());
        }).toList();
        List<LiveErrand> errandViews = active.stream().map(e -> new LiveErrand(e.getId(), e.getCode(), e.getStatus(),
                e.firstStop().getLat(), e.firstStop().getLng(), e.getCourierId(), e.isNeedsAttention())).toList();
        return new LiveMap(courierViews, errandViews);
    }

    /** AC-006-04: purga diaria de pings (Habeas Data). */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Bogota")
    public void purgeOldPings() {
        pings.deleteOlderThan(Instant.now().minus(PING_RETENTION));
    }
}
