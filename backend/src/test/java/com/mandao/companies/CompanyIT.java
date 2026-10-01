package com.mandao.companies;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 011 · Mandao Empresas. */
class CompanyIT extends IntegrationTest {

    @Test
    @DisplayName("AC-011-01..07 · empresa, miembros, direcciones, historial y reporte")
    void companyLifecycle() throws Exception {
        Session admin = admin();
        String adminPhone = randomPhone();
        perform(post("/api/admin/companies"), admin, Map.of("name", "Constructora Sierra Nevada",
                "nit", "900555111-2", "plan", "EMPRESAS", "adminFullName", "Laura Maestre",
                "adminPhone", adminPhone, "adminPassword", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyFee").value(99_000));

        // Creado por el admin: debe aceptar los términos antes de operar (AC-001-13).
        Session boss = acceptTerms(login(adminPhone));
        perform(get("/api/company"), boss).andExpect(jsonPath("$.name").value("Constructora Sierra Nevada"));

        String memberPhone = randomPhone();
        perform(post("/api/company/members"), boss, Map.of("fullName", "Juan Mensajero", "phone", memberPhone,
                "password", PASSWORD)).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CLIENT"));
        Session member = acceptTerms(login(memberPhone));

        // AC-011-05 · direcciones compartidas
        JsonNode address = read(perform(post("/api/company/addresses"), boss, Map.of("label", "Obra Novalito",
                "address", "Novalito, Valledupar", "lat", 10.4700, "lng", -73.2600))
                .andExpect(status().isCreated()).andReturn());
        assertThat(getJson("/api/me/addresses", member).findValuesAsText("label")).contains("Obra Novalito");
        perform(get("/api/company/addresses"), member).andExpect(status().isForbidden());

        // AC-011-03 / AC-011-04
        JsonNode errand = createErrand(member, errandBody("EMPRESARIAL", "FIXED", null));
        assertThat(errand.get("companyId").asText()).isNotBlank();
        assertThat(getJson("/api/company/errands", boss).findValuesAsText("id"))
                .contains(errand.get("id").asText());
        perform(get("/api/errands/" + errand.get("id").asText()), boss)
                .andExpect(jsonPath("$.viewer").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.startOtp").doesNotExist());

        // Otra empresa no ve nada
        String otherPhone = randomPhone();
        perform(post("/api/admin/companies"), admin, Map.of("name", "Otra SAS", "plan", "PYME",
                "adminFullName", "Otro Jefe", "adminPhone", otherPhone, "adminPassword", PASSWORD))
                .andExpect(status().isCreated());
        Session otherBoss = acceptTerms(login(otherPhone));
        assertThat(getJson("/api/company/errands", otherBoss)).isEmpty();
        perform(get("/api/errands/" + errand.get("id").asText()), otherBoss).andExpect(status().isNotFound());
        perform(delete("/api/company/addresses/" + address.get("id").asText()), otherBoss)
                .andExpect(status().isNotFound());

        // AC-011-06 · reporte mensual
        String month = YearMonth.now(ZoneId.of("America/Bogota")).toString();
        perform(get("/api/company/report?month=" + month), boss)
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains(errand.get("code").asText()));

        perform(delete("/api/company/addresses/" + address.get("id").asText()), boss)
                .andExpect(status().isNoContent());
    }
}
