package com.mandao.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import com.mandao.analytics.AnalyticsEventRepository;
import com.mandao.payments.LedgerEntry;
import com.mandao.payments.LedgerEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo de un mandado de compra con bolsa (specs 002, 004, 005, 007, 008). */
class ErrandFlowIT extends IntegrationTest {

    @Autowired
    LedgerEntryRepository ledger;
    @Autowired
    AnalyticsEventRepository events;

    static byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder evidence(UUID errandId, Session courier, String type) {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/courier/errands/" + errandId + "/evidences");
        b.param("type", type).header("Authorization", "Bearer " + courier.token());
        return b;
    }

    @Test
    @DisplayName("Flujo completo: cotizar → pedir → aceptar → OTP → evidencias → entregar → confirmar → calificar")
    void fullPurchaseFlow() throws Exception {
        Session client = client();
        Session courier = approvedCourier(10.4640, -73.2540);
        Session rival = approvedCourier(10.4650, -73.2550);
        Session stranger = client();

        // AC-002-03 · cotización con desglose
        perform(post("/api/quotes"), client, Map.of("complexity", "MEDIA", "estimatedWaitMinutes", 0,
                "stops", List.of(Map.of("lat", ORIGIN[0], "lng", ORIGIN[1]),
                        Map.of("lat", DESTINATION[0], "lng", DESTINATION[1]))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.waitCharge").value(0))
                .andExpect(jsonPath("$.complexity").value(2500))
                .andExpect(jsonPath("$.tariffVersion").isNumber());

        // AC-002-05 / AC-002-07 · se crea en SEARCHING con OTPs solo para el dueño
        Map<String, Object> body = errandBody("COMPRA", "FIXED", 80_000L);
        body.put("totalPrice", 1); // el servidor ignora precios enviados por el cliente
        JsonNode errand = createErrand(client, body);
        UUID id = UUID.fromString(errand.get("id").asText());
        assertThat(errand.get("status").asText()).isEqualTo("SEARCHING");
        assertThat(errand.get("totalPrice").asLong()).isGreaterThan(5000);
        assertThat(errand.get("startOtp").asText()).matches("\\d{4}");
        String startOtp = errand.get("startOtp").asText();
        String endOtp = errand.get("endOtp").asText();

        // AC-001-08 · un extraño recibe 404
        perform(get("/api/errands/" + id), stranger).andExpect(status().isNotFound());

        // AC-004-01 · el mandadero lo ve en oportunidades, sin datos de contacto del cliente
        JsonNode opps = getJson("/api/courier/opportunities", courier);
        assertThat(opps.findValuesAsText("id")).contains(id.toString());
        JsonNode prospectView = getJson("/api/errands/" + id, courier);
        assertThat(prospectView.get("viewer").asText()).isEqualTo("PROSPECT");
        assertThat(prospectView.at("/client/phone").isMissingNode() || prospectView.at("/client/phone").isNull())
                .isTrue();
        assertThat(prospectView.has("startOtp")).isFalse();

        // AC-004-02 / AC-004-03 · acepta uno; el segundo recibe 409
        perform(post("/api/courier/errands/" + id + "/accept"), courier)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.startOtp").doesNotExist());
        perform(post("/api/courier/errands/" + id + "/accept"), rival).andExpect(status().isConflict());

        // AC-004-04 · no puede tomar otro mientras tenga uno activo
        JsonNode other = createErrand(client, errandBody("ENVIO", "FIXED", null));
        perform(post("/api/courier/errands/" + other.get("id").asText() + "/accept"), courier)
                .andExpect(status().isConflict());

        // AC-005-05 · sin "voy en camino" no se aceptan evidencias
        mvc.perform(evidence(id, courier, "NOTA").param("note", "hola")).andExpect(status().isConflict());

        // AC-005-01 / AC-005-02
        perform(post("/api/courier/errands/" + id + "/on-the-way"), courier)
                .andExpect(jsonPath("$.status").value("GOING_TO_ORIGIN"));
        String wrong = startOtp.equals("0000") ? "1111" : "0000";
        perform(post("/api/courier/errands/" + id + "/start"), courier, Map.of("otp", wrong))
                .andExpect(status().isUnprocessableEntity());
        perform(post("/api/courier/errands/" + id + "/start"), courier, Map.of("otp", startOtp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // AC-006-01 · el ping en servicio se guarda
        perform(post("/api/courier/location"), courier, Map.of("lat", 10.4700, "lng", -73.2480, "accuracy", 12))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/errands/" + id + "/track", client)).hasSize(1);

        // AC-005-06 · archivo falso ⇒ 400
        mvc.perform(evidence(id, courier, "FOTO").file(new MockMultipartFile("file", "x.jpg", "image/jpeg",
                "no soy imagen".getBytes(StandardCharsets.UTF_8)))).andExpect(status().isBadRequest());

        // Foto válida con GPS
        String photoResp = mvc.perform(evidence(id, courier, "FOTO")
                        .file(new MockMultipartFile("file", "foto.jpg", "image/jpeg", jpeg()))
                        .param("lat", "10.47").param("lng", "-73.25").param("note", "Llegué a la droguería"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID photoId = UUID.fromString(mapper.readTree(photoResp).get("id").asText());

        // AC-007-02 · factura por encima de la bolsa ⇒ 422; factura válida se registra
        mvc.perform(evidence(id, courier, "FACTURA")
                        .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", jpeg())).param("amount", "90000"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(evidence(id, courier, "FACTURA")
                        .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", jpeg())).param("amount", "63700"))
                .andExpect(status().isCreated());

        // AC-005-07 · el cliente ve la foto; un extraño no
        perform(get("/api/files/" + photoId), client).andExpect(status().isOk());
        perform(get("/api/files/" + photoId), stranger).andExpect(status().isNotFound());
        perform(get("/api/files/" + photoId), rival).andExpect(status().isNotFound());

        // AC-005-04 · OTP de fin
        perform(post("/api/courier/errands/" + id + "/deliver"), courier, Map.of("otp", endOtp))
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // AC-005-08 · el cliente confirma ⇒ COMPLETED; AC-007-03 · liquidación con devolución $16.300
        perform(post("/api/errands/" + id + "/confirm"), client)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.purchasesTotal").value(63_700))
                .andExpect(jsonPath("$.refundTotal").value(16_300));
        assertThat(ledger.findByErrandId(id)).extracting(LedgerEntry::getType).contains(
                LedgerEntry.Type.HOLD, LedgerEntry.Type.PURCHASE, LedgerEntry.Type.REFUND,
                LedgerEntry.Type.COURIER_PAYOUT, LedgerEntry.Type.PLATFORM_FEE);

        // AC-008-01 / AC-008-02
        perform(post("/api/errands/" + id + "/rating"), client,
                Map.of("stars", 5, "tags", List.of("RAPIDO", "AMABLE"), "comment", "Excelente"))
                .andExpect(jsonPath("$.status").value("RATED"))
                .andExpect(jsonPath("$.rating.stars").value(5));
        perform(post("/api/errands/" + id + "/rating"), client, Map.of("stars", 4)).andExpect(status().isConflict());

        // AC-008-03 · reputación
        perform(get("/api/couriers/" + courier.id() + "/reputation"), client)
                .andExpect(jsonPath("$.averageStars").value(5.0))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.tags.RAPIDO").value(1));

        // AC-007-05 · ganancias del mandadero pendientes de pago
        perform(get("/api/courier/earnings"), courier)
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.pendingPayout").isNumber());

        // AC-002-09 · eventos del embudo
        assertThat(events.findByName("quote_viewed")).isNotEmpty();
        assertThat(events.findByName("errand_requested")).extracting(e -> e.getErrandId()).contains(id);
        assertThat(events.findByName("errand_completed")).extracting(e -> e.getErrandId()).contains(id);

        // AC-002-08 · repetir mandado
        Map<String, Object> repeat = errandBody("COMPRA", "FIXED", 50_000L);
        repeat.put("repeatOf", id.toString());
        createErrand(client, repeat);
        assertThat(events.findByName("repeat_order")).isNotEmpty();
        Map<String, Object> foreignRepeat = errandBody("ENVIO", "FIXED", null);
        foreignRepeat.put("repeatOf", id.toString());
        perform(post("/api/errands"), stranger, foreignRepeat).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-005-03 · 5 OTP incorrectos bloquean el código (423) y alertan al operador")
    void otpLockout() throws Exception {
        Session client = client();
        Session courier = approvedCourier(10.4631, -73.2532);
        JsonNode errand = createErrand(client, errandBody("ENVIO", "FIXED", null));
        String id = errand.get("id").asText();
        String wrong = errand.get("startOtp").asText().equals("0000") ? "1111" : "0000";
        perform(post("/api/courier/errands/" + id + "/accept"), courier).andExpect(status().isOk());
        perform(post("/api/courier/errands/" + id + "/on-the-way"), courier).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            perform(post("/api/courier/errands/" + id + "/start"), courier, Map.of("otp", wrong))
                    .andExpect(status().isUnprocessableEntity());
        }
        perform(post("/api/courier/errands/" + id + "/start"), courier, Map.of("otp", wrong))
                .andExpect(status().isLocked());
        perform(post("/api/courier/errands/" + id + "/start"), courier,
                Map.of("otp", errand.get("startOtp").asText())).andExpect(status().isLocked());
        Session admin = admin();
        perform(get("/api/errands/" + id), admin)
                .andExpect(jsonPath("$.needsAttention").value(true))
                .andExpect(jsonPath("$.otpFailedAttempts").value(5))
                .andExpect(jsonPath("$.startOtp").doesNotExist());
    }

    @Test
    @DisplayName("AC-002-06 / AC-002-04 / AC-002-11 · reglas de creación y cancelación")
    void creationRules() throws Exception {
        Session client = client();
        perform(post("/api/errands"), client, errandBody("ENVIO", "FIXED", 20_000L))
                .andExpect(status().isBadRequest());

        Map<String, Object> far = errandBody("ENVIO", "FIXED", null);
        far.put("stops", List.of(Map.of("address", "Bogotá", "lat", 4.711, "lng", -74.0721)));
        perform(post("/api/errands"), client, far).andExpect(status().isBadRequest());

        Map<String, Object> noStops = errandBody("ENVIO", "FIXED", null);
        noStops.put("stops", List.of());
        perform(post("/api/errands"), client, noStops).andExpect(status().isBadRequest());

        JsonNode errand = createErrand(client, errandBody("DILIGENCIA", "FIXED", null));
        perform(post("/api/errands/" + errand.get("id").asText() + "/cancel"), client, Map.of("reason", "Ya no"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("VOIDED"));
    }

    @Test
    @DisplayName("Un mandadero no puede solicitar mandados")
    void courierCannotRequest() throws Exception {
        Session courier = approvedCourier(10.46, -73.25);
        perform(post("/api/errands"), courier, errandBody("ENVIO", "FIXED", null)).andExpect(status().isForbidden());
    }
}
