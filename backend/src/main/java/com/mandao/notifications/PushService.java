package com.mandao.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mandao.identity.Role;
import com.mandao.identity.UserRepository;
import com.mandao.shared.ApiException;
import com.mandao.shared.MandaoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envío de notificaciones Web Push (spec 012). Siempre después del commit y asíncrono:
 * una falla del servicio de push nunca afecta la operación del negocio.
 */
@Service
public class PushService {

    /** Contenido mínimo, sin datos personales. {@code url} es la ruta de la PWA que se abre al tocar. */
    public record Message(String title, String body, String url, String tag) {
    }

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final Duration TTL = Duration.ofHours(1);

    private final PushSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final WebPushCrypto.Vapid vapid;
    private final String subject;
    private final List<String> allowedHosts;
    private final boolean allowHttp;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PushService(PushSubscriptionRepository subscriptions, UserRepository users, ObjectMapper mapper,
                       MandaoProperties props) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.mapper = mapper;
        MandaoProperties.Push cfg = props.push();
        this.subject = cfg == null || cfg.subject() == null ? "mailto:soporte@mandao.co" : cfg.subject();
        this.allowedHosts = cfg == null || cfg.allowedHosts() == null ? List.of() : cfg.allowedHosts();
        this.allowHttp = cfg != null && cfg.allowHttp();
        if (cfg != null && notBlank(cfg.publicKey()) && notBlank(cfg.privateKey())) {
            this.vapid = WebPushCrypto.vapid(cfg.publicKey(), cfg.privateKey());
        } else {
            this.vapid = WebPushCrypto.vapid(WebPushCrypto.generateKeyPair());
            log.warn("VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY sin definir: se usan llaves temporales. "
                    + "Las suscripciones dejarán de funcionar al reiniciar (solo desarrollo).");
        }
    }

    /** AC-012-01. */
    public String publicKey() {
        return vapid.publicKeyBase64();
    }

    /** AC-012-02: alta o actualización de la suscripción del dispositivo. */
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        validateEndpoint(endpoint);
        try {
            if (WebPushCrypto.b64d(p256dh).length != 65 || WebPushCrypto.b64d(auth).length != 16) {
                throw ApiException.badRequest("Llaves de suscripción inválidas");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Llaves de suscripción inválidas");
        }
        PushSubscription sub = subscriptions.findByEndpoint(endpoint)
                .map(s -> {
                    s.refresh(userId, p256dh, auth);
                    return s;
                })
                .orElseGet(() -> new PushSubscription(userId, endpoint, p256dh, auth));
        subscriptions.save(sub);
    }

    /** AC-012-02: solo borra si la suscripción es del usuario actual. */
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptions.deleteByEndpointAndUserId(endpoint, userId);
    }

    public void notifyUser(UUID userId, Message message) {
        if (userId != null) {
            notifyUsers(List.of(userId), message);
        }
    }

    public void notifyRole(Role role, Message message) {
        afterCommit(() -> send(users.findActiveIdsByRole(role), message));
    }

    public void notifyUsers(Collection<UUID> userIds, Message message) {
        if (userIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(userIds);
        afterCommit(() -> send(ids, message));
    }

    private void send(List<UUID> userIds, Message message) {
        if (userIds.isEmpty()) {
            return;
        }
        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(payload(message));
        } catch (Exception e) {
            log.warn("No se pudo serializar el push", e);
            return;
        }
        for (PushSubscription sub : subscriptions.findByUserIdIn(userIds)) {
            try {
                byte[] body = WebPushCrypto.encrypt(payload, sub.getP256dh(), sub.getAuth());
                HttpRequest request = HttpRequest.newBuilder(URI.create(sub.getEndpoint()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/octet-stream")
                        .header("Content-Encoding", "aes128gcm")
                        .header("TTL", String.valueOf(TTL.toSeconds()))
                        .header("Urgency", "high")
                        .header("Authorization", WebPushCrypto.vapidAuthorization(sub.getEndpoint(), vapid, subject,
                                Instant.now()))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> handleResponse(sub, res.statusCode()))
                        .exceptionally(err -> {
                            log.warn("Push no entregado ({}): {}", host(sub.getEndpoint()), err.getMessage());
                            return null;
                        });
            } catch (RuntimeException e) {
                log.warn("Push no enviado ({}): {}", host(sub.getEndpoint()), e.getMessage());
            }
        }
    }

    /** AC-012-05: 404/410 ⇒ la suscripción venció o el usuario la revocó. */
    private void handleResponse(PushSubscription sub, int status) {
        if (status == 404 || status == 410) {
            subscriptions.deleteByEndpoint(sub.getEndpoint());
        } else if (status >= 200 && status < 300) {
            subscriptions.markUsed(sub.getEndpoint(), Instant.now());
        } else {
            log.warn("Servicio de push respondió {} ({})", status, host(sub.getEndpoint()));
        }
    }

    /** Formato que entiende el service worker de Angular (ngsw): muestra la notificación y navega al tocarla. */
    static Map<String, Object> payload(Message m) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", m.title());
        notification.put("body", m.body());
        notification.put("icon", "/icons/icon-192x192.png");
        notification.put("badge", "/icons/icon-96x96.png");
        notification.put("lang", "es-CO");
        notification.put("vibrate", List.of(200, 100, 200));
        if (m.tag() != null) {
            notification.put("tag", m.tag());
            notification.put("renotify", true);
        }
        notification.put("data", Map.of("onActionClick", Map.of("default",
                Map.of("operation", "navigateLastFocusedOrOpen", "url", m.url()))));
        return Map.of("notification", notification);
    }

    /** Evita SSRF: solo HTTPS hacia servicios de push conocidos. */
    void validateEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest("Endpoint de push inválido");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean schemeOk = "https".equals(scheme) || (allowHttp && "http".equals(scheme));
        boolean hostOk = host != null && allowedHosts.stream()
                .anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equalsIgnoreCase(h));
        if (!schemeOk || !hostOk || endpoint.length() > 1000 || uri.getUserInfo() != null) {
            throw ApiException.badRequest("Servicio de notificaciones no soportado");
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } else {
            runSafely(action);
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Falló el envío de notificaciones push", e);
        }
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
