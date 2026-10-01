package com.mandao.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.mandao.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordIT extends IntegrationTest {

    private static final String NEW_PASSWORD = "OtraClave2026";

    @Test
    @DisplayName("AC-001-10 · cambiar contraseña revoca las sesiones anteriores y abre una nueva")
    void changePassword() throws Exception {
        Session c = client();

        perform(put("/api/me/password"), c, Map.of("currentPassword", "incorrecta1", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        perform(put("/api/me/password"), c, Map.of("currentPassword", PASSWORD, "newPassword", "corta"))
                .andExpect(status().isBadRequest());

        MvcResult ok = perform(put("/api/me/password"), c,
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        assertThat(ok.getResponse().getCookie("mandao_rt")).isNotNull();

        // La sesión anterior ya no se puede renovar.
        mvc.perform(post("/api/auth/refresh").cookie(c.refreshCookie())).andExpect(status().isUnauthorized());
        // La contraseña vieja ya no sirve; la nueva sí.
        loginWith(c.phone(), PASSWORD).andExpect(status().isUnauthorized());
        loginWith(c.phone(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-001-11 · el admin genera una contraseña temporal y el usuario debe cambiarla")
    void adminReset() throws Exception {
        Session c = client();
        Session admin = admin();

        perform(post("/api/admin/users/" + c.id() + "/reset-password"), c).andExpect(status().isForbidden());
        perform(post("/api/admin/users/" + admin.id() + "/reset-password"), admin).andExpect(status().isBadRequest());

        JsonNode res = read(perform(post("/api/admin/users/" + c.id() + "/reset-password"), admin)
                .andExpect(status().isOk()).andReturn());
        String temporary = res.get("temporaryPassword").asText();
        assertThat(temporary).matches(AuthDtos.PASSWORD_REGEX).hasSize(10);

        // Sesiones del usuario revocadas y contraseña anterior inválida.
        mvc.perform(post("/api/auth/refresh").cookie(c.refreshCookie())).andExpect(status().isUnauthorized());
        loginWith(c.phone(), PASSWORD).andExpect(status().isUnauthorized());

        JsonNode login = read(loginWith(c.phone(), temporary).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true)).andReturn());
        Session temp = new Session(login.get("accessToken").asText(), c.id(), c.phone(), null);

        perform(put("/api/me/password"), temp, Map.of("currentPassword", temporary, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("Contraseña temporal: 10 caracteres, letras y dígitos, sin caracteres ambiguos")
    void temporaryPasswordShape() {
        for (int i = 0; i < 200; i++) {
            String p = AuthService.temporaryPassword();
            assertThat(p).matches(AuthDtos.PASSWORD_REGEX).hasSize(10).doesNotContainPattern("[0O1lI]");
        }
    }

    private org.springframework.test.web.servlet.ResultActions loginWith(String phone, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phone", phone, "password", password))));
    }
}
