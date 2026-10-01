package com.mandao.shared;

import com.mandao.identity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** Usuario autenticado extraído del JWT de la petición actual. */
public record AuthUser(UUID id, Role role, UUID companyId) {

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return fromJwt(token.getToken());
        }
        throw ApiException.unauthorized("Sesión requerida");
    }

    public static AuthUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken token ? fromJwt(token.getToken()) : null;
    }

    public static AuthUser fromJwt(Jwt jwt) {
        String company = jwt.getClaimAsString("cid");
        return new AuthUser(UUID.fromString(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")),
                company == null ? null : UUID.fromString(company));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
