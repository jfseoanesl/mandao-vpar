package com.mandao.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/** Rate limiting por IP en rutas sensibles (login, registro, OTP, cotización). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, Pattern path, int limit, long windowSeconds) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("login", Pattern.compile("^/api/auth/login$"), 5, 60),
            new Rule("register", Pattern.compile("^/api/auth/register$"), 20, 3600),
            new Rule("password", Pattern.compile("^/api/me/password$"), 5, 60),
            new Rule("refresh", Pattern.compile("^/api/auth/refresh$"), 30, 60),
            new Rule("quote", Pattern.compile("^/api/quotes$"), 30, 60),
            new Rule("otp", Pattern.compile("^/api/courier/errands/[^/]+/(start|deliver)$"), 10, 60),
            new Rule("events", Pattern.compile("^/api/events$"), 60, 60));

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            for (Rule rule : RULES) {
                if (rule.path().matcher(path).matches()
                        && !limiter.tryAcquire(rule.name() + ":" + request.getRemoteAddr(), rule.limit(),
                        rule.windowSeconds())) {
                    response.setStatus(429);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"status\":429,\"title\":\"Too Many Requests\","
                                    + "\"detail\":\"Demasiados intentos. Espera un momento e intenta de nuevo.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
