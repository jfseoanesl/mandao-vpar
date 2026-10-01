package com.mandao.orders;

import com.mandao.analytics.AnalyticsService;
import com.mandao.orders.ErrandDtos.CreateErrandRequest;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.orders.ErrandDtos.StopRequest;
import com.mandao.payments.SettlementService;
import com.mandao.pricing.GeoPoint;
import com.mandao.pricing.PricingEngine;
import com.mandao.pricing.PricingService;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Operaciones del cliente sobre sus mandados (spec 002). */
@Service
public class ErrandService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ErrandRepository errands;
    private final ErrandAccess access;
    private final ErrandLifecycle lifecycle;
    private final ErrandViews views;
    private final PricingService pricing;
    private final SettlementService settlement;
    private final AnalyticsService analytics;
    private final OtpCipher otp;

    public ErrandService(ErrandRepository errands, ErrandAccess access, ErrandLifecycle lifecycle, ErrandViews views,
                         PricingService pricing, SettlementService settlement, AnalyticsService analytics,
                         OtpCipher otp) {
        this.errands = errands;
        this.access = access;
        this.lifecycle = lifecycle;
        this.views = views;
        this.pricing = pricing;
        this.settlement = settlement;
        this.analytics = analytics;
        this.otp = otp;
    }

    @Transactional
    public ErrandView create(CreateErrandRequest req, AuthUser user) {
        if (!user.role().canRequestErrands()) {
            throw ApiException.forbidden("Solo los clientes pueden solicitar mandados");
        }
        long budget = req.purchaseBudget() == null ? 0 : req.purchaseBudget();
        if (budget > 0 && !req.category().allowsPurchaseBudget()) {
            throw ApiException.badRequest("La bolsa de compras solo aplica para compras");
        }
        validateSchedule(req.scheduledFor());
        if (req.repeatOf() != null) {
            access.requireOwner(req.repeatOf(), user);
        }

        List<GeoPoint> points = req.stops().stream().map(s -> new GeoPoint(s.lat(), s.lng())).toList();
        PricingEngine.Breakdown quote = pricing.quote(points, req.estimatedWaitMinutes(), req.complexity());

        Errand errand = new Errand(newCode(), user.id(), user.companyId(), req.category(), req.description().trim(),
                req.complexity(), req.pricingMode(), req.estimatedWaitMinutes(),
                BigDecimal.valueOf(quote.distanceKm()), quote.total(), quote.platformFee(), quote.courierPayout(),
                quote.tariffVersion(), budget, req.paymentMethod(), req.scheduledFor(), req.repeatOf(),
                otp.encrypt(OtpCipher.generate()), otp.encrypt(OtpCipher.generate()));
        for (StopRequest s : req.stops()) {
            errand.addStop(s.address().trim(), s.lat(), s.lng(), blankToNull(s.instructions()),
                    blankToNull(s.contactName()), blankToNull(s.contactPhone()));
        }
        errands.save(errand);
        lifecycle.created(errand, user.id());
        settlement.onCreated(errand);
        analytics.track("errand_requested", user.id(), errand.getId(), errand.getCategory().name(), null);
        if (req.repeatOf() != null) {
            analytics.track("repeat_order", user.id(), errand.getId(), req.repeatOf().toString(), null);
        }
        return views.view(errand, ErrandAccess.Viewer.OWNER);
    }

    @Transactional(readOnly = true)
    public List<ErrandSummary> mine(AuthUser user) {
        return errands.findByClientIdOrderByCreatedAtDesc(user.id(), PageRequest.of(0, 100)).stream()
                .map(views::summary).toList();
    }

    @Transactional(readOnly = true)
    public ErrandView get(UUID id, AuthUser user) {
        ErrandAccess.Access a = access.resolve(id, user, true);
        return views.view(a.errand(), a.viewer());
    }

    /** AC-002-11. */
    @Transactional
    public ErrandView cancel(UUID id, String reason, AuthUser user) {
        Errand errand = access.requireOwner(id, user);
        if (!ErrandStatus.CLIENT_CANCELLABLE.contains(errand.getStatus())) {
            throw ApiException.conflict("El mandado ya está en ejecución y no se puede cancelar desde la app");
        }
        errand.setCancelReason(blankToNull(reason));
        lifecycle.transition(errand, ErrandStatus.CANCELLED, user.id(), reason);
        return views.view(errand, ErrandAccess.Viewer.OWNER);
    }

    /** AC-005-08. */
    @Transactional
    public ErrandView confirm(UUID id, AuthUser user) {
        Errand errand = access.requireOwner(id, user);
        lifecycle.transition(errand, ErrandStatus.COMPLETED, user.id(), "Confirmado por el cliente");
        return views.view(errand, ErrandAccess.Viewer.OWNER);
    }

    private static void validateSchedule(Instant scheduledFor) {
        if (scheduledFor == null) {
            return;
        }
        Instant now = Instant.now();
        if (scheduledFor.isBefore(now.plus(Duration.ofMinutes(10)))
                || scheduledFor.isAfter(now.plus(Duration.ofDays(7)))) {
            throw ApiException.badRequest("Programa el mandado entre 10 minutos y 7 días a partir de ahora");
        }
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("M");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!errands.existsByCode(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("No se pudo generar un código único");
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
