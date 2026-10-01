package com.mandao.notifications;

import com.mandao.notifications.PushService.Message;
import com.mandao.orders.Errand;
import com.mandao.orders.ErrandStatus;

import java.text.NumberFormat;
import java.util.Locale;

/** Textos de las notificaciones (tabla de la spec 012). Sin direcciones ni datos personales. */
public final class PushMessages {

    private static final Locale CO = Locale.forLanguageTag("es-CO");

    private PushMessages() {
    }

    public static Message newOpportunity(Errand e) {
        return new Message("🛵 Nuevo mandado cerca",
                "Ganas " + money(e.getCourierPayout()) + ". Tócalo para verlo antes que otro.",
                "/mandadero", "opportunity-" + e.getId());
    }

    public static Message countered(Errand e, long amount) {
        return new Message("Te hicieron una contraoferta", "El cliente propone " + money(amount) + " (" + e.getCode() + ").",
                "/mandadero", "offer-" + e.getId());
    }

    public static Message offerReceived(Errand e) {
        return new Message("Recibiste una oferta", "Un mandadero quiere hacer tu mandado " + e.getCode() + ".",
                clientUrl(e), "errand-" + e.getId());
    }

    public static Message needsAttention(Errand e) {
        return new Message("⚠ Mandado sin asignar", e.getCode() + " necesita asignación manual.",
                "/admin/mandados/" + e.getId(), "attention-" + e.getId());
    }

    public static Message courierCancelled(Errand e) {
        return new Message("Mandado cancelado", "El mandado " + e.getCode() + " fue cancelado.", "/mandadero",
                "errand-" + e.getId());
    }

    /** Mensaje al cliente para el nuevo estado, o null si ese estado no se notifica. */
    public static Message forClient(Errand e, ErrandStatus to) {
        String text = switch (to) {
            case ASSIGNED -> "¡Ya tienes mandadero! Mira quién va a hacer tu mandado.";
            case GOING_TO_ORIGIN -> "Tu mandadero va en camino.";
            case IN_PROGRESS -> "Empezamos tu mandado.";
            case DELIVERED -> "Tu mandado fue entregado. Revisa las evidencias y confírmalo.";
            case FAILED -> "No pudimos completar tu mandado. Te contactaremos.";
            default -> null;
        };
        return text == null ? null : new Message("Mandado " + e.getCode(), text, clientUrl(e), "errand-" + e.getId());
    }

    private static String clientUrl(Errand e) {
        return "/app/mandados/" + e.getId();
    }

    static String money(long cop) {
        NumberFormat f = NumberFormat.getCurrencyInstance(CO);
        f.setMaximumFractionDigits(0);
        f.setCurrency(java.util.Currency.getInstance("COP"));
        return f.format(cop);
    }
}
