package com.mandao.payments;

import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandRepository;
import com.mandao.orders.ErrandStatus;
import com.mandao.shared.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pagos manuales (AC-007-04) y ganancias del mandadero (AC-007-05). */
@RestController
public class PaymentController {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public record ConfirmRequest(@Size(max = 80) String reference) {
    }

    public record PaymentView(UUID id, UUID errandId, String errandCode, ErrandStatus errandStatus, String clientName,
                              PaymentMethod method, long amount, Payment.Status status, String reference,
                              Instant confirmedAt, long purchases, long refund, long courierPayout,
                              String courierName, Instant courierPaidAt, Instant createdAt) {
    }

    public record EarningItem(UUID errandId, String code, ErrandStatus status, long payout, Instant completedAt,
                              Instant paidAt) {
    }

    public record Earnings(long today, long week, long pendingPayout, long paidTotal, long completedCount,
                           List<EarningItem> items) {
    }

    private final PaymentRepository payments;
    private final ErrandRepository errands;
    private final UserRepository users;
    private final SettlementService settlement;

    public PaymentController(PaymentRepository payments, ErrandRepository errands, UserRepository users,
                             SettlementService settlement) {
        this.payments = payments;
        this.errands = errands;
        this.users = users;
        this.settlement = settlement;
    }

    @GetMapping("/api/admin/payments")
    @Transactional(readOnly = true)
    public List<PaymentView> list(@RequestParam(value = "status", required = false) Payment.Status status) {
        List<Payment> list = status == null ? payments.findTop300ByOrderByCreatedAtDesc()
                : payments.findTop300ByStatusOrderByCreatedAtDesc(status);
        Map<UUID, Errand> byId = errands.findAllById(list.stream().map(Payment::getErrandId).toList()).stream()
                .collect(Collectors.toMap(Errand::getId, Function.identity()));
        Map<UUID, User> people = users.findAllById(byId.values().stream()
                        .flatMap(e -> java.util.stream.Stream.of(e.getClientId(), e.getCourierId()))
                        .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().filter(p -> byId.containsKey(p.getErrandId())).map(p -> {
            Errand e = byId.get(p.getErrandId());
            User client = people.get(e.getClientId());
            User courier = e.getCourierId() == null ? null : people.get(e.getCourierId());
            return new PaymentView(p.getId(), e.getId(), e.getCode(), e.getStatus(),
                    client == null ? null : client.getFullName(), p.getMethod(), p.getAmount(), p.getStatus(),
                    p.getReference(), p.getConfirmedAt(), settlement.purchasesTotal(e.getId()),
                    settlement.refundTotal(e.getId()), e.getCourierPayout(),
                    courier == null ? null : courier.getFullName(), e.getCourierPaidAt(), p.getCreatedAt());
        }).toList();
    }

    @PostMapping("/api/admin/payments/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable("id") UUID id, @Valid @RequestBody ConfirmRequest request) {
        Payment p = settlement.confirm(id, AuthUser.current().id(), request.reference());
        return Map.of("id", p.getId(), "status", p.getStatus());
    }

    @GetMapping("/api/courier/earnings")
    @Transactional(readOnly = true)
    public Earnings earnings() {
        UUID courierId = AuthUser.current().id();
        List<Errand> done = errands.findByCourierIdOrderByCreatedAtDesc(courierId, PageRequest.of(0, 500)).stream()
                .filter(e -> ErrandStatus.DONE.contains(e.getStatus())).toList();
        Instant startOfDay = LocalDate.now(BOGOTA).atStartOfDay(BOGOTA).toInstant();
        Instant startOfWeek = LocalDate.now(BOGOTA).minusDays(6).atStartOfDay(BOGOTA).toInstant();
        long today = done.stream().filter(e -> e.getCompletedAt() != null && e.getCompletedAt().isAfter(startOfDay))
                .mapToLong(Errand::getCourierPayout).sum();
        long week = done.stream().filter(e -> e.getCompletedAt() != null && e.getCompletedAt().isAfter(startOfWeek))
                .mapToLong(Errand::getCourierPayout).sum();
        long pending = done.stream().filter(e -> e.getCourierPaidAt() == null).mapToLong(Errand::getCourierPayout).sum();
        long paid = done.stream().filter(e -> e.getCourierPaidAt() != null).mapToLong(Errand::getCourierPayout).sum();
        List<EarningItem> items = done.stream().limit(50).map(e -> new EarningItem(e.getId(), e.getCode(),
                e.getStatus(), e.getCourierPayout(), e.getCompletedAt(), e.getCourierPaidAt())).toList();
        return new Earnings(today, week, pending, paid, done.size(), items);
    }
}
