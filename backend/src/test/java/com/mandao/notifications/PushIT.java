package com.mandao.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import com.mandao.matching.DispatchService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 012: un servidor HTTP local hace de servicio de push (FCM/Mozilla) y el test hace de navegador. */
class PushIT extends IntegrationTest {

    /** Zona apartada para que el despachador elija a este mandadero y no a los de otras pruebas. */
    private static final double LAT = 10.4012;
    private static final double LNG = -73.2201;

    record Received(String authorization, String encoding, String ttl, byte[] body) {
    }

    @Autowired
    DispatchService dispatch;
    @Autowired
    PushSubscriptionRepository subscriptions;
    @Autowired
    PushService push;

    private HttpServer server;
    private final LinkedBlockingQueue<Received> inbox = new LinkedBlockingQueue<>();
    private final AtomicInteger responseStatus = new AtomicInteger(201);

    @BeforeEach
    void startPushServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            inbox.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Encoding"),
                    exchange.getRequestHeaders().getFirst("TTL"), body));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopPushServer() {
        server.stop(0);
    }

    private String endpoint(String id) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push/" + id;
    }

    private Map<String, Object> subscription(String endpoint, KeyPair browser, byte[] auth) {
        return Map.of("endpoint", endpoint, "keys", Map.of(
                "p256dh", WebPushCrypto.b64(WebPushCrypto.encodePoint((ECPublicKey) browser.getPublic())),
                "auth", WebPushCrypto.b64(auth)));
    }

    @Test
    @DisplayName("AC-012-01/02 · llave pública y alta/baja de suscripción solo por su dueño")
    void subscriptionLifecycle() throws Exception {
        Session c = client();
        Session other = client();
        perform(get("/api/push/key"), null).andExpect(status().isUnauthorized());
        JsonNode key = getJson("/api/push/key", c);
        assertThat(WebPushCrypto.b64d(key.get("publicKey").asText())).hasSize(65);

        String ep = endpoint("lifecycle");
        perform(post("/api/push/subscriptions"), c, subscription(ep, WebPushCrypto.generateKeyPair(),
                WebPushCrypto.randomBytes(16))).andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(ep)).isPresent();

        // Otro usuario no puede borrarla.
        perform(delete("/api/push/subscriptions").param("endpoint", ep), other).andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(ep)).isPresent();

        perform(delete("/api/push/subscriptions").param("endpoint", ep), c).andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(ep)).isEmpty();
    }

    @Test
    @DisplayName("Seguridad · se rechazan endpoints fuera de los servicios de push conocidos (SSRF) y llaves inválidas")
    void rejectsUnknownEndpoints() throws Exception {
        Session c = client();
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] auth = WebPushCrypto.randomBytes(16);
        for (String bad : List.of("https://evil.example.com/push", "http://169.254.169.254/latest/meta-data",
                "https://fcm.googleapis.com.evil.com/x", "file:///etc/passwd")) {
            perform(post("/api/push/subscriptions"), c, subscription(bad, browser, auth))
                    .andExpect(status().isBadRequest());
        }
        perform(post("/api/push/subscriptions"), c, Map.of("endpoint", "https://fcm.googleapis.com/fcm/send/x",
                "keys", Map.of("p256dh", "corta", "auth", "corta"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-012-03/04 · el mandadero recibe el mandado cercano cifrado, con VAPID, y lo descifra")
    void courierReceivesOpportunity() throws Exception {
        Session courier = approvedCourier(LAT, LNG);
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] auth = WebPushCrypto.randomBytes(16);
        perform(post("/api/push/subscriptions"), courier, subscription(endpoint("courier"), browser, auth))
                .andExpect(status().isNoContent());

        Session c = client();
        Map<String, Object> body = errandBody("ENVIO", "FIXED", null);
        body.put("stops", List.of(Map.of("address", "Punto A", "lat", LAT, "lng", LNG),
                Map.of("address", "Punto B", "lat", LAT + 0.01, "lng", LNG)));
        JsonNode errand = createErrand(c, body);
        dispatch.dispatch();

        Received r = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(r).as("push recibido").isNotNull();
        assertThat(r.encoding()).isEqualTo("aes128gcm");
        assertThat(r.ttl()).isEqualTo("3600");
        assertThat(r.authorization()).startsWith("vapid t=").contains(", k=");

        JsonNode message = mapper.readTree(WebPushCryptoTest.decrypt(r.body(), browser, auth));
        JsonNode n = message.get("notification");
        assertThat(n.get("title").asText()).contains("Nuevo mandado");
        assertThat(n.get("body").asText()).startsWith("Ganas");
        assertThat(errand.get("code").asText()).isNotBlank();
        assertThat(n.at("/data/onActionClick/default/url").asText()).isEqualTo("/mandadero");
        // Sin datos personales en el contenido.
        assertThat(message.toString()).doesNotContain("Punto A").doesNotContain(c.phone());
    }

    @Test
    @DisplayName("AC-012-05 · si el servicio responde 410 la suscripción se elimina")
    void goneSubscriptionIsRemoved() throws Exception {
        Session c = client();
        String ep = endpoint("gone");
        perform(post("/api/push/subscriptions"), c, subscription(ep, WebPushCrypto.generateKeyPair(),
                WebPushCrypto.randomBytes(16))).andExpect(status().isNoContent());
        responseStatus.set(410);

        push.notifyUser(c.id(), new PushService.Message("Prueba", "Texto", "/app", null));

        assertThat(inbox.poll(10, TimeUnit.SECONDS)).isNotNull();
        long deadline = System.currentTimeMillis() + 5_000;
        while (subscriptions.findByEndpoint(ep).isPresent() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(subscriptions.findByEndpoint(ep)).isEmpty();
    }
}
