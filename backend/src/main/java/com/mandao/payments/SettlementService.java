package com.mandao.payments;

import com.mandao.orders.Errand;
import com.mandao.shared.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Bolsa de compras y cierre económico de cada mandado (spec 007). */
@Service
public class SettlementService {

    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;

    public SettlementService(PaymentGateway gateway, PaymentRepository payments, LedgerEntryRepository ledger) {
        this.gateway = gateway;
        this.payments = payments;
        this.ledger = ledger;
    }

    /** AC-007-01. */
    @Transactional
    public void onCreated(Errand errand) {
        gateway.open(errand.getId(), errand.getPaymentMethod(), errand.getTotalPrice() + errand.getPurchaseBudget());
        if (errand.getPurchaseBudget() > 0) {
            ledger.save(new LedgerEntry(errand.getId(), LedgerEntry.Type.HOLD, errand.getPurchaseBudget()));
        }
    }

    /** Se invoca cuando cambia el precio (negociación aceptada o ajuste del admin). */
    @Transactional
    public void onPriceChanged(Errand errand) {
        payments.findByErrandId(errand.getId())
                .ifPresent(p -> p.setAmount(errand.getTotalPrice() + errand.getPurchaseBudget()));
    }

    /** AC-007-02: una factura no puede superar lo que queda de la bolsa. */
    @Transactional
    public void recordPurchase(Errand errand, long amount) {
        if (amount <= 0) {
            throw ApiException.badRequest("El valor de la factura debe ser mayor a cero");
        }
        long spent = purchasesTotal(errand.getId());
        if (spent + amount > errand.getPurchaseBudget()) {
            throw ApiException.unprocessable("La compra supera la bolsa autorizada por el cliente ($"
                    + (errand.getPurchaseBudget() - spent) + " disponibles)");
        }
        ledger.save(new LedgerEntry(errand.getId(), LedgerEntry.Type.PURCHASE, amount));
    }

    /** AC-007-03. */
    @Transactional
    public void settle(Errand errand) {
        long purchases = purchasesTotal(errand.getId());
        Settlement.compute(errand.getPurchaseBudget(), purchases, errand.getPlatformFee(), errand.getCourierPayout())
                .forEach(line -> ledger.save(new LedgerEntry(errand.getId(), line.type(), line.amount())));
    }

    /** AC-007-06. */
    @Transactional
    public void onCancelled(Errand errand) {
        payments.findByErrandId(errand.getId()).ifPresent(gateway::cancel);
    }

    @Transactional
    public Payment confirm(UUID paymentId, UUID adminId, String reference) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Pago"));
        if (payment.getStatus() != Payment.Status.PENDING) {
            throw ApiException.conflict("El pago ya está " + payment.getStatus());
        }
        gateway.confirm(payment, adminId, reference);
        return payment;
    }

    public long purchasesTotal(UUID errandId) {
        return sum(errandId, LedgerEntry.Type.PURCHASE);
    }

    public long refundTotal(UUID errandId) {
        return sum(errandId, LedgerEntry.Type.REFUND);
    }

    public Payment.Status paymentStatus(UUID errandId) {
        return payments.findByErrandId(errandId).map(Payment::getStatus).orElse(null);
    }

    private long sum(UUID errandId, LedgerEntry.Type type) {
        return ledger.findByErrandId(errandId).stream()
                .filter(e -> e.getType() == type).mapToLong(LedgerEntry::getAmount).sum();
    }
}
