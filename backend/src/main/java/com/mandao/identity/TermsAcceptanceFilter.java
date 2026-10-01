package com.mandao.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AC-001-13: mientras el usuario no acepte la versión vigente de los términos, la API solo permite lo
 * indispensable para aceptarlos, cambiar la contraseña o salir. La versión aceptada viaja en el token ("tv").
 */
public class TermsAcceptanceFilter extends OncePerRequestFilter {

    static final String CODE = "TERMS_PENDING";

    /** Pendiente si no es admin y la versión del token no es la vigente. */
    public static boolean isPending(Jwt jwt) {
        return !Role.ADMIN.name().equals(jwt.getClaimAsString("role"))
                && !AuthDtos.TERMS_VERSION.equals(jwt.getClaimAsString("tv"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && isPending(token.getToken()) && !allowed(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Debes aceptar los términos y condiciones vigentes para continuar.\","
                    + "\"code\":\"" + CODE + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        return path.startsWith("/api/auth/") || path.startsWith("/api/public/")
                || (HttpMethod.GET.matches(method) && path.equals("/api/me"))
                || (HttpMethod.POST.matches(method) && path.equals("/api/me/terms"))
                || (HttpMethod.PUT.matches(method) && path.equals("/api/me/password"))
                || (HttpMethod.DELETE.matches(method) && path.equals("/api/push/subscriptions"))
                || !path.startsWith("/api/");
    }
}
