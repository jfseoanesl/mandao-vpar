package com.mandao.orders;

import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.orders.ErrandDtos.ErrandSummary;
import com.mandao.orders.ErrandDtos.ErrandView;
import com.mandao.payments.SettlementService;
import com.mandao.pricing.PricingEngine;
import com.mandao.pricing.PricingService;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuditService;
import com.mandao.shared.FileResponses;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Operación del piloto desde el panel (spec 010). */
@Service
public class AdminErrandService {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BOGOTA);

    private final ErrandRepository errands;
    private final AssignmentService assignment;
    private final ErrandLifecycle lifecycle;
    private final ErrandViews views;
    private final PricingService pricing;
    private final SettlementService settlement;
    private final UserRepository users;
    private final AuditService audit;

    public AdminErrandService(ErrandRepository errands, AssignmentService assignment, ErrandLifecycle lifecycle,
                              ErrandViews views, PricingService pricing, SettlementService settlement,
                              UserRepository users, AuditService audit) {
        this.errands = errands;
        this.assignment = assignment;
        this.lifecycle = lifecycle;
        this.views = views;
        this.pricing = pricing;
        this.settlement = settlement;
        this.users = users;
        this.audit = audit;
    }

    /** AC-010-02: los que requieren atención primero. */
    @Transactional(readOnly = true)
    public List<ErrandSummary> list(ErrandStatus status) {
        return errands.findForAdmin(status, PageRequest.of(0, 200)).stream().map(views::summary).toList();
    }

    /** AC-004-06. */
    @Transactional
    public ErrandView assign(UUID errandId, UUID courierId, UUID adminId) {
        Errand errand = load(errandId);
        assignment.assign(errand, courierId, null, adminId, "Asignado por operador");
        audit.record(adminId, "ERRAND_ASSIGNED", "errand", errandId, courierId.toString());
        return views.view(errand, ErrandAccess.Viewer.ADMIN);
    }

    /** AC-010-03. */
    @Transactional
    public ErrandView cancel(UUID errandId, String reason, UUID adminId) {
        Errand errand = load(errandId);
        errand.setCancelReason(reason);
        ErrandStatus target = ErrandStateMachine.canTransition(errand.getStatus(), ErrandStatus.CANCELLED)
                ? ErrandStatus.CANCELLED : ErrandStatus.FAILED;
        lifecycle.transition(errand, target, adminId, "Operador: " + (reason == null ? "" : reason));
        audit.record(adminId, "ERRAND_" + target.name(), "errand", errandId, reason);
        return views.view(errand, ErrandAccess.Viewer.ADMIN);
    }

    /** AC-010-03. */
    @Transactional
    public ErrandView adjustPrice(UUID errandId, long amount, UUID adminId) {
        Errand errand = load(errandId);
        if (ErrandStatus.FINAL.contains(errand.getStatus())) {
            throw ApiException.conflict("No se puede cambiar el precio de un mandado cerrado");
        }
        long before = errand.getTotalPrice();
        PricingEngine.Split split = PricingEngine.split(amount, pricing.activeTariff().getCommissionPct());
        errand.setPrice(amount, split.platformFee(), split.courierPayout());
        settlement.onPriceChanged(errand);
        errands.save(errand);
        audit.record(adminId, "ERRAND_PRICE_CHANGED", "errand", errandId, before + " -> " + amount);
        return views.view(errand, ErrandAccess.Viewer.ADMIN);
    }

    @Transactional
    public ErrandView resolveAttention(UUID errandId, UUID adminId) {
        Errand errand = load(errandId);
        errand.resolveAttention();
        errands.save(errand);
        audit.record(adminId, "ERRAND_ATTENTION_RESOLVED", "errand", errandId, null);
        return views.view(errand, ErrandAccess.Viewer.ADMIN);
    }

    /** AC-007-05. */
    @Transactional
    public ErrandView markCourierPaid(UUID errandId, UUID adminId) {
        Errand errand = load(errandId);
        if (!ErrandStatus.DONE.contains(errand.getStatus())) {
            throw ApiException.conflict("Solo se paga al mandadero cuando el mandado está completado");
        }
        if (errand.getCourierPaidAt() != null) {
            throw ApiException.conflict("Este mandado ya fue pagado al mandadero");
        }
        errand.markCourierPaid();
        errands.save(errand);
        audit.record(adminId, "COURIER_PAID", "errand", errandId, String.valueOf(errand.getCourierPayout()));
        return views.view(errand, ErrandAccess.Viewer.ADMIN);
    }

    /** AC-010-04: sin datos de documentos de identidad. */
    @Transactional(readOnly = true)
    public String exportCsv(Instant from, Instant to) {
        List<Errand> list = errands.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        Map<UUID, User> people = users.findAllById(list.stream()
                        .flatMap(e -> java.util.stream.Stream.of(e.getClientId(), e.getCourierId()))
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        StringBuilder sb = new StringBuilder(
                "codigo,creado,categoria,estado,modo,cliente,empresa,mandadero,km,precio,tarifa_plataforma,"
                        + "pago_mandadero,bolsa,compras,metodo_pago,asignado_min,entregado,calificado\n");
        for (Errand e : list) {
            User client = people.get(e.getClientId());
            User courier = e.getCourierId() == null ? null : people.get(e.getCourierId());
            Long assignMin = e.getAssignedAt() == null ? null
                    : java.time.Duration.between(e.getCreatedAt(), e.getAssignedAt()).toMinutes();
            sb.append(String.join(",",
                    FileResponses.csvCell(e.getCode()),
                    FileResponses.csvCell(CSV_DATE.format(e.getCreatedAt())),
                    FileResponses.csvCell(e.getCategory()),
                    FileResponses.csvCell(e.getStatus()),
                    FileResponses.csvCell(e.getPricingMode()),
                    FileResponses.csvCell(client == null ? null : client.getFullName()),
                    FileResponses.csvCell(e.getCompanyId() != null ? "si" : "no"),
                    FileResponses.csvCell(courier == null ? null : courier.getFullName()),
                    FileResponses.csvCell(e.getDistanceKm()),
                    FileResponses.csvCell(e.getTotalPrice()),
                    FileResponses.csvCell(e.getPlatformFee()),
                    FileResponses.csvCell(e.getCourierPayout()),
                    FileResponses.csvCell(e.getPurchaseBudget()),
                    FileResponses.csvCell(settlement.purchasesTotal(e.getId())),
                    FileResponses.csvCell(e.getPaymentMethod()),
                    FileResponses.csvCell(assignMin),
                    FileResponses.csvCell(e.getDeliveredAt() == null ? null : CSV_DATE.format(e.getDeliveredAt())),
                    FileResponses.csvCell(e.getStatus() == ErrandStatus.RATED ? "si" : "no")));
            sb.append('\n');
        }
        return sb.toString();
    }

    private Errand load(UUID id) {
        return errands.findById(id).orElseThrow(() -> ApiException.notFound("Mandado"));
    }
}
