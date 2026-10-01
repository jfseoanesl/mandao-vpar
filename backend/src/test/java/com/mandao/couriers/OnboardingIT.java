package com.mandao.couriers;

import com.mandao.IntegrationTest;
import com.mandao.shared.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 009 · onboarding de mandaderos y spec 006 · privacidad de ubicación. */
class OnboardingIT extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLog;

    static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private void upload(Session courier, String type) throws Exception {
        mvc.perform(multipart("/api/courier/documents").file(new MockMultipartFile("file", "d.png", "image/png", png()))
                        .param("type", type).header("Authorization", "Bearer " + courier.token()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-009-01..06 · de registro a aprobación")
    void onboarding() throws Exception {
        Session courier = register("COURIER", "Nuevo Mandadero");
        Session admin = admin();

        perform(get("/api/courier/me"), courier)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.missingDocuments.length()").value(3));

        // AC-009-01 · no puede ponerse disponible
        perform(put("/api/courier/availability"), courier, Map.of("available", true)).andExpect(status().isConflict());
        // AC-006-02 · tampoco compartir ubicación
        perform(post("/api/courier/location"), courier, Map.of("lat", 10.46, "lng", -73.25))
                .andExpect(status().isConflict());

        perform(put("/api/courier/me"), courier, Map.of("vehicleType", "MOTO", "plate", "abc12d",
                "payoutMethod", "NEQUI", "payoutAccount", "3001112233"))
                .andExpect(jsonPath("$.plate").value("ABC12D"))
                .andExpect(jsonPath("$.missingDocuments.length()").value(6));

        upload(courier, "CEDULA_FRENTE");
        upload(courier, "CEDULA_REVERSO");
        upload(courier, "SELFIE");

        // AC-009-03 · faltan SOAT/licencia/tarjeta por ser moto
        perform(post("/api/admin/couriers/" + courier.id() + "/approve"), admin)
                .andExpect(status().isUnprocessableEntity());

        // AC-009-04 · rechazo con motivo
        perform(post("/api/admin/couriers/" + courier.id() + "/reject"), admin, Map.of("reason", "Falta el SOAT"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        perform(get("/api/courier/me"), courier).andExpect(jsonPath("$.rejectionReason").value("Falta el SOAT"));

        // AC-009-02 · puede corregir; vuelve a PENDING
        upload(courier, "SOAT");
        upload(courier, "LICENCIA");
        upload(courier, "TARJETA_PROPIEDAD");
        perform(get("/api/courier/me"), courier).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.missingDocuments.length()").value(0));

        perform(post("/api/admin/couriers/" + courier.id() + "/approve"), admin)
                .andExpect(jsonPath("$.status").value("APPROVED"));
        perform(put("/api/courier/availability"), courier, Map.of("available", true))
                .andExpect(jsonPath("$.available").value(true));
        perform(post("/api/courier/location"), courier, Map.of("lat", 10.46, "lng", -73.25))
                .andExpect(status().isNoContent());

        // AC-009-05 · documentos solo para admin
        String docId = getJson("/api/courier/me", courier).at("/documents/0/id").asText();
        perform(get("/api/admin/documents/" + docId), admin).andExpect(status().isOk());
        perform(get("/api/admin/documents/" + docId), courier).andExpect(status().isForbidden());
        perform(get("/api/files/" + docId), courier).andExpect(status().isNotFound());

        // AC-009-06 · auditoría
        assertThat(auditLog.findByEntityId(courier.id().toString()))
                .extracting(a -> a.getAction()).contains("COURIER_REJECTED", "COURIER_APPROVED");
    }

    @Test
    @DisplayName("AC-004-05 · un mandadero no aprobado no puede aceptar mandados")
    void pendingCannotAccept() throws Exception {
        Session client = client();
        Session pending = register("COURIER", "Pendiente");
        String id = createErrand(client, errandBody("ENVIO", "FIXED", null)).get("id").asText();
        perform(post("/api/courier/errands/" + id + "/accept"), pending).andExpect(status().isForbidden());
        perform(get("/api/courier/opportunities"), pending).andExpect(jsonPath("$.length()").value(0));
    }
}
