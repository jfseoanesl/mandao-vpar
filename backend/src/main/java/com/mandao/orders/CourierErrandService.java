package com.mandao.orders;

import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.OfferView;
import com.mandao.orders.ErrandDtos.OpportunityView;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import com.mandao.shared.Geo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Flujo del mandadero: oportunidades, aceptar, ejecutar con OTP (specs 004 y 005). */
@Service
public class CourierErrandService {

    static final double OPPORTUNITY_RADIUS_KM = 8.0;
    static final int MAX_OTP_ATTEMPTS = 5;
    static final Duration SCHEDULE_LEAD = Duration.ofMinutes(15);

    private final ErrandRepository errands;
    private final ErrandOfferRepository offers;
    private final CourierProfileRepository couriers;
    private final ErrandAccess access;
    private final AssignmentService assignment;
    private final ErrandLifecycle lifecycle;
    private final ErrandViews views;
    private final OtpCipher otp;

    public CourierErrandService(ErrandRepository errands, ErrandOfferRepository offers,
                                CourierProfileRepository couriers, ErrandAccess access, AssignmentService assignment,
                                ErrandLifecycle lifecycle, ErrandViews views, OtpCipher otp) {
        this.errands = errands;
        this.offers = offers;
        this.couriers = couriers;
        this.access = access;
        this.assignment = assignment;
        this.lifecycle = lifecycle;
        this.views = views;
        this.otp = otp;
    }

    /** AC-004-01: mandados abiertos a ≤ 8 km (o todos si aún no hay ubicación), programados dentro de 15 min. */
    @Transactional(readOnly = true)
    public List<OpportunityView> opportunities(AuthUser courier) {
        CourierProfile profile = couriers.findById(courier.id()).orElseThrow(() -> ApiException.notFound("Mandadero"));
        if (!profile.isApproved()) {
            return List.of();
        }
        Instant horizon = Instant.now().plus(SCHEDULE_LEAD);
        List<Errand> open = errands.findByStatusIn(ErrandStatus.OPEN).stream()
                .filter(e -> e.getScheduledFor() == null || !e.getScheduledFor().isAfter(horizon))
                .toList();
        Map<UUID, ErrandOffer> myOffers = offers.findByCourierIdAndErrandIdIn(courier.id(),
                        open.stream().map(Errand::getId).toList()).stream()
                .collect(Collectors.toMap(ErrandOffer::getErrandId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));

        return open.stream()
                .map(e -> {
                    ErrandStop origin = e.firstStop();
                    Double distance = profile.getLat() == null ? null
                            : Math.round(Geo.haversineKm(profile.getLat(), profile.getLng(), origin.getLat(),
                            origin.getLng()) * 10) / 10.0;
                    ErrandOffer mine = myOffers.get(e.getId());
                    OfferView myOffer = mine == null ? null : views.offer(mine, false);
                    ErrandStop last = e.getStops().get(e.getStops().size() - 1);
                    return new OpportunityView(e.getId(), e.getCode(), e.getCategory(), e.getDescription(),
                            e.getComplexity(), e.getPricingMode(), e.getStatus(), e.getCourierPayout(),
                            e.getQuotedPrice(), e.getDistanceKm(), distance, e.getStops().size(),
                            origin.getAddress(), e.getStops().size() > 1 ? last.getAddress() : null,
                            e.getPurchaseBudget(), e.getCreatedAt(), e.getScheduledFor(), myOffer);
                })
                .filter(o -> o.distanceToOriginKm() == null || o.distanceToOriginKm() <= OPPORTUNITY_RADIUS_KM)
                .sorted(Comparator.comparing(OpportunityView::distanceToOriginKm,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** AC-004-02..05. */
    @Transactional
    public ErrandView accept(UUID errandId, AuthUser courier) {
        assignment.ensureCourierCanTake(courier.id());
        Errand errand = errands.findById(errandId).orElseThrow(() -> ApiException.notFound("Mandado"));
        if (errand.getStatus() != ErrandStatus.SEARCHING) {
            throw ApiException.conflict(errand.getStatus() == ErrandStatus.NEGOTIATING
                    ? "Este mandado es negociable: envía una oferta" : "Este mandado ya fue tomado");
        }
        assignment.assign(errand, courier.id(), null, courier.id(), null);
        return views.view(errand, ErrandAccess.Viewer.COURIER);
    }

    /** AC-005-01. */
    @Transactional
    public ErrandView onTheWay(UUID errandId, AuthUser courier) {
        Errand errand = access.requireAssignedCourier(errandId, courier);
        lifecycle.transition(errand, ErrandStatus.GOING_TO_ORIGIN, courier.id(), null);
        return views.view(errand, ErrandAccess.Viewer.COURIER);
    }

    /** AC-005-02 / AC-005-03. Los intentos fallidos se guardan aunque la respuesta sea error. */
    @Transactional(noRollbackFor = ApiException.class)
    public ErrandView start(UUID errandId, String code, AuthUser courier) {
        Errand errand = access.requireAssignedCourier(errandId, courier);
        if (errand.getStatus() != ErrandStatus.GOING_TO_ORIGIN) {
            throw ApiException.conflict("Primero marca que vas en camino");
        }
        verifyOtp(errand, errand.getStartOtpEnc(), code);
        lifecycle.transition(errand, ErrandStatus.IN_PROGRESS, courier.id(), "Código de inicio validado");
        return views.view(errand, ErrandAccess.Viewer.COURIER);
    }

    /** AC-005-04. */
    @Transactional(noRollbackFor = ApiException.class)
    public ErrandView deliver(UUID errandId, String code, AuthUser courier) {
        Errand errand = access.requireAssignedCourier(errandId, courier);
        if (errand.getStatus() != ErrandStatus.IN_PROGRESS) {
            throw ApiException.conflict("El mandado no está en ejecución");
        }
        verifyOtp(errand, errand.getEndOtpEnc(), code);
        lifecycle.transition(errand, ErrandStatus.DELIVERED, courier.id(), "Código de finalización validado");
        return views.view(errand, ErrandAccess.Viewer.COURIER);
    }

    @Transactional
    public ErrandView fail(UUID errandId, String reason, AuthUser courier) {
        Errand errand = access.requireAssignedCourier(errandId, courier);
        errand.flagAttention("Mandadero reportó que no pudo completar: " + (reason == null ? "" : reason));
        lifecycle.transition(errand, ErrandStatus.FAILED, courier.id(), reason);
        return views.view(errand, ErrandAccess.Viewer.COURIER);
    }

    @Transactional(readOnly = true)
    public List<ErrandSummary> mine(AuthUser courier) {
        return errands.findByCourierIdOrderByCreatedAtDesc(courier.id(), PageRequest.of(0, 100)).stream()
                .map(views::summary).toList();
    }

    private void verifyOtp(Errand errand, String encrypted, String code) {
        if (errand.getOtpFailedAttempts() >= MAX_OTP_ATTEMPTS) {
            throw ApiException.locked("Código bloqueado por intentos fallidos. Soporte Mandao te contactará.");
        }
        if (!otp.matches(encrypted, code)) {
            int attempts = errand.registerFailedOtp();
            if (attempts >= MAX_OTP_ATTEMPTS) {
                errand.flagAttention("Código OTP bloqueado tras " + attempts + " intentos fallidos");
                errands.save(errand);
                throw ApiException.locked("Código bloqueado por intentos fallidos. Soporte Mandao te contactará.");
            }
            errands.save(errand);
            throw ApiException.unprocessable("Código incorrecto. Pídeselo al cliente. Intentos restantes: "
                    + (MAX_OTP_ATTEMPTS - attempts));
        }
    }
}
