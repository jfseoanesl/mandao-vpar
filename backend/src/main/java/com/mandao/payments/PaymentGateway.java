package com.mandao.payments;

import java.util.UUID;

/**
 * Puerto de pagos (P6). El MVP usa {@link ManualPaymentGateway}; una integración con Wompi
 * implementará esta misma interfaz (crear transacción, webhooks de confirmación, anulación).
 */
public interface PaymentGateway {

    /** Abre el cobro de un mandado. */
    Payment open(UUID errandId, PaymentMethod method, long amount);

    /** Marca el cobro como recibido. En pasarela real lo haría el webhook. */
    void confirm(Payment payment, UUID actorId, String reference);

    /** Anula el cobro (mandado cancelado antes de ejecutarse). */
    void cancel(Payment payment);
}
