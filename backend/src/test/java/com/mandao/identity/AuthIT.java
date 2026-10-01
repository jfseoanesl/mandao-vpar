package com.mandao.identity;

import com.mandao.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTest {

    @Test
    @DisplayName("AC-001-01 · registro de cliente y mandadero devuelve token y rol")
    void registerRoles() throws Exception {
        Session c = register("CLIENT", "Ana Castro");
        perform(get("/api/me"), c).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CLIENT"));
        Session m = register("COURIER", "Luz Daza");
        perform(get("/api/me"), m).andExpect(jsonPath("$.role").value("COURIER"));
        assertThat(users.findById(c.id()).orElseThrow().getPrivacyAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-001-02 · sin aceptar tratamiento de datos se rechaza")
    void privacyRequired() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Sin Consentimiento", "phone", randomPhone(),
                                "password", PASSWORD, "role", "CLIENT", "acceptPrivacy", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptPrivacy").exists());
    }

    @Test
    @DisplayName("Validaciones: celular colombiano y contraseña con letras y números")
    void inputValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "X Y", "phone", "12345", "password", "corta",
                                "role", "CLIENT", "acceptPrivacy", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("AC-001-03 · celular duplicado ⇒ 409")
    void duplicatePhone() throws Exception {
        Session c = client();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Otro", "phone", c.phone(), "password", PASSWORD,
                                "role", "CLIENT", "acceptPrivacy", true))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("AC-001-04 · credenciales incorrectas ⇒ 401 genérico")
    void badCredentials() throws Exception {
        Session c = client();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", c.phone(), "password", "OtraClave123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Celular o contraseña incorrectos"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "3999999999", "password", "OtraClave123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Celular o contraseña incorrectos"));
    }

    @Test
    @DisplayName("AC-001-05 · más de 5 intentos de login por minuto ⇒ 429")
    void loginRateLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("phone", "3111111111", "password", "Nada12345"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", "3111111111", "password", "Nada12345"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("AC-001-06 · refresh rota el token; reutilizar uno viejo revoca la sesión")
    void refreshRotationAndReuseDetection() throws Exception {
        Session c = client();
        Cookie original = c.refreshCookie();
        assertThat(original.isHttpOnly()).isTrue();
        assertThat(original.getPath()).isEqualTo("/api/auth");

        MvcResult first = mvc.perform(post("/api/auth/refresh").cookie(original))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists()).andReturn();
        Cookie rotated = first.getResponse().getCookie("mandao_rt");
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        // Reuso del token viejo ⇒ 401 y se revoca toda la familia (el rotado también deja de servir).
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-001-12 · el registro guarda la versión de los términos aceptada y la fecha")
    void termsAcceptanceRecorded() throws Exception {
        Session c = client();
        User user = users.findById(c.id()).orElseThrow();
        assertThat(user.getTermsVersion()).isEqualTo(AuthDtos.TERMS_VERSION);
        assertThat(user.getTermsAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("AC-001-07 · logout revoca el refresh token")
    void logout() throws Exception {
        Session c = client();
        mvc.perform(post("/api/auth/logout").cookie(c.refreshCookie())).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(c.refreshCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-001-09 · rutas por rol")
    void roleGuards() throws Exception {
        Session c = client();
        perform(get("/api/admin/kpis"), c).andExpect(status().isForbidden());
        perform(get("/api/courier/me"), c).andExpect(status().isForbidden());
        perform(get("/api/company"), c).andExpect(status().isForbidden());
        perform(get("/api/me"), null).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cabeceras de seguridad presentes")
    void securityHeaders() throws Exception {
        Session c = client();
        perform(get("/api/me"), c)
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
                    assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
                    assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                            .contains("frame-ancestors 'none'");
                });
    }
}
