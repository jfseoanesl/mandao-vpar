package com.mandao.identity;

import com.mandao.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TermsIT extends IntegrationTest {

    /** Usuario creado por otra vía distinta al registro (p. ej. por una empresa o por el admin). */
    private Session createdByOthers() throws Exception {
        String phone = randomPhone();
        users.save(new User("Juan Mensajero", phone, null, encoder.encode(PASSWORD), Role.CLIENT, null, null));
        return login(phone);
    }

    @Test
    @DisplayName("AC-001-13 · sin aceptar los términos vigentes la API solo permite lo indispensable")
    void pendingUserIsRestricted() throws Exception {
        Session s = createdByOthers();

        perform(get("/api/me"), s).andExpect(status().isOk()).andExpect(jsonPath("$.termsPending").value(true));
        perform(get("/api/errands"), s).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_PENDING"));
        perform(post("/api/errands"), s, errandBody("ENVIO", "FIXED", null)).andExpect(status().isForbidden());
        perform(get("/api/push/key"), s).andExpect(status().isForbidden());
        // Permitidos: salir del dispositivo y cambiar la contraseña.
        perform(delete("/api/push/subscriptions").param("endpoint", "https://fcm.googleapis.com/x"), s)
                .andExpect(status().isNoContent());
        perform(put("/api/me/password"), s, Map.of("currentPassword", "incorrecta1", "newPassword", "OtraClave2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-001-14 · aceptar la versión vigente desbloquea la cuenta; otra versión ⇒ 409")
    void acceptTermsUnlocks() throws Exception {
        Session s = createdByOthers();
        perform(post("/api/me/terms"), s, Map.of("version", "0.1")).andExpect(status().isConflict());

        Session accepted = acceptTerms(s);
        perform(get("/api/me"), accepted).andExpect(jsonPath("$.termsPending").value(false));
        perform(get("/api/errands"), accepted).andExpect(status().isOk());

        User user = users.findById(s.id()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getTermsVersion()).isEqualTo(AuthDtos.TERMS_VERSION);
        org.assertj.core.api.Assertions.assertThat(user.getTermsAcceptedAt()).isNotNull();
        // Al renovar la sesión, el token nuevo también queda desbloqueado.
        mvc.perform(post("/api/auth/refresh").cookie(accepted.refreshCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.termsPending").value(false));
    }

    @Test
    @DisplayName("AC-001-13 · al publicar una versión nueva, quien aceptó la anterior vuelve a quedar pendiente")
    void newVersionRequiresAcceptance() throws Exception {
        Session c = client();
        perform(get("/api/errands"), c).andExpect(status().isOk());

        User user = users.findById(c.id()).orElseThrow();
        user.acceptTerms("0.9");
        users.save(user);

        Session again = login(c.phone());
        perform(get("/api/errands"), again).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TERMS_PENDING"));
    }

    @Test
    @DisplayName("AC-001-13 · los admins no aceptan términos de usuario")
    void adminsAreExempt() throws Exception {
        Session admin = admin();
        perform(get("/api/me"), admin).andExpect(jsonPath("$.termsPending").value(false));
        perform(get("/api/admin/kpis"), admin).andExpect(status().isOk());
    }
}
