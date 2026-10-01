package com.mandao.payments;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Pagos registrados a mano por el operador: efectivo, Nequi, Daviplata o transferencia. */
@Component
public class ManualPaymentGateway implements PaymentGateway {

    private final PaymentRepository payments;

    public ManualPaymentGateway(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public Payment open(UUID errandId, PaymentMethod method, long amount) {
        return payments.save(new Payment(errandId, method, amount));
    }

    @Override
    public void confirm(Payment payment, UUID actorId, String reference) {
        payment.confirm(actorId, reference);
    }

    @Override
    public void cancel(Payment payment) {
        payment.voidPayment();
    }
}
