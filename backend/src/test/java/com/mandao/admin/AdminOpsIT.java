package com.mandao.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import com.mandao.matching.DispatchService;
import com.mandao.orders.ErrandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 010 · operación del piloto y spec 004 · despacho. */
class AdminOpsIT extends IntegrationTest {

    @Autowired
    DispatchService dispatch;
    @Autowired
    ErrandRepository errands;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("AC-004-07 · a los 5 min sin asignar se marca atención; AC-004-06 · asignación manual")
    void staleErrandAndManualAssignment() throws Exception {
        Session client = client();
        Session admin = admin();
        Session courier = approvedCourier(10.4632, -73.2531);
        UUID id = UUID.fromString(createErrand(client, errandBody("ENVIO", "FIXED", null)).get("id").asText());

        // Consulta PostGIS de candidatos: el mandadero cercano aparece
        assertThat(couriers.findNearbyCandidates(IntegrationTest.ORIGIN[0], IntegrationTest.ORIGIN[1], 2000,
                java.time.Instant.now().minusSeconds(120), 50)).contains(courier.id());

        jdbc.update("UPDATE errands SET created_at = now() - interval '6 minutes' WHERE id = ?", id);
        dispatch.dispatch();
        assertThat(errands.findById(id).orElseThrow().isNeedsAttention()).isTrue();

        // AC-010-02 · los que requieren atención van primero
        JsonNode list = getJson("/api/admin/errands?status=SEARCHING", admin);
        assertThat(list.get(0).get("needsAttention").asBoolean()).isTrue();

        perform(post("/api/admin/errands/" + id + "/assign"), admin, Map.of("courierId", courier.id()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.needsAttention").value(false));
    }

    @Test
    @DisplayName("AC-010-03 · ajuste de precio y cancelación por el operador; AC-007-04 confirmar pago")
    void adjustCancelAndPayments() throws Exception {
        Session client = client();
        Session admin = admin();
        String id = createErrand(client, errandBody("DILIGENCIA", "FIXED", null)).get("id").asText();

        perform(put("/api/admin/errands/" + id + "/price"), admin, Map.of("amount", 20_000))
                .andExpect(jsonPath("$.totalPrice").value(20_000))
                .andExpect(jsonPath("$.platformFee").value(4_500));

        JsonNode payments = getJson("/api/admin/payments?status=PENDING", admin);
        String paymentId = null;
        for (JsonNode p : payments) {
            if (p.get("errandId").asText().equals(id)) {
                paymentId = p.get("id").asText();
                assertThat(p.get("amount").asLong()).isEqualTo(20_000);
            }
        }
        assertThat(paymentId).isNotNull();
        perform(post("/api/admin/payments/" + paymentId + "/confirm"), admin, Map.of("reference", "NEQUI-123"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        perform(post("/api/admin/payments/" + paymentId + "/confirm"), admin, Map.of("reference", "x"))
                .andExpect(status().isConflict());

        perform(post("/api/admin/errands/" + id + "/cancel"), admin, Map.of("reason", "Cliente llamó"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("AC-010-01 · KPIs con valor y objetivo; AC-010-04 · exportación CSV")
    void kpisAndExport() throws Exception {
        Session client = client();
        Session admin = admin();
        createErrand(client, errandBody("ENVIO", "FIXED", null));

        JsonNode report = getJson("/api/admin/kpis", admin);
        assertThat(report.get("kpis")).hasSize(6);
        assertThat(report.get("kpis").findValuesAsText("key")).containsExactly("errandsPerDay", "avgTicket",
                "assignMinutes", "completionRate", "avgRating", "repeatRate");
        assertThat(report.at("/kpis/0/target").asDouble()).isEqualTo(15.0);
        assertThat(report.at("/totals/errands").asLong()).isPositive();
        assertThat(report.get("daily")).isNotEmpty();
        assertThat(report.get("byChannel").findValuesAsText("key")).contains("test");

        String today = LocalDate.now().toString();
        perform(get("/api/admin/export/errands?from=" + LocalDate.now().minusDays(1) + "&to=" + today), admin)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .startsWith("﻿codigo,creado,categoria"));
    }

    @Test
    @DisplayName("AC-010-05 · nueva versión de tarifa")
    void tariffVersioning() throws Exception {
        Session admin = admin();
        JsonNode current = getJson("/api/admin/tariff", admin);
        int version = current.get("version").asInt();
        perform(put("/api/admin/tariff"), admin, Map.of("baseFare", 5500, "perKm", 950, "includedWaitMin", 10,
                "perExtraMin", 250, "perExtraStop", 2000, "complexityMedium", 2500, "complexityComplex", 5000,
                "commissionPct", 0.2))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.baseFare").value(5500));
        // se restaura la tarifa del documento para no afectar a otras pruebas
        perform(put("/api/admin/tariff"), admin, Map.of("baseFare", 5000, "perKm", 900, "includedWaitMin", 10,
                "perExtraMin", 250, "perExtraStop", 2000, "complexityMedium", 2500, "complexityComplex", 5000,
                "commissionPct", 0.225))
                .andExpect(jsonPath("$.version").value(version + 2));
    }

    @Test
    @DisplayName("AC-010-06 · mapa en vivo")
    void liveMap() throws Exception {
        Session admin = admin();
        Session courier = approvedCourier(10.47, -73.25);
        JsonNode live = getJson("/api/admin/live", admin);
        assertThat(live.get("couriers").findValuesAsText("id")).contains(courier.id().toString());
    }
}
