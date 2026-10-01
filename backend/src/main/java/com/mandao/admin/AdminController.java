package com.mandao.admin;

import com.mandao.identity.AuthDtos;
import com.mandao.identity.AuthDtos.UserView;
import com.mandao.identity.AuthService;
import com.mandao.identity.RefreshTokenRepository;
import com.mandao.identity.User;
import com.mandao.identity.UserRepository;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuditService;
import com.mandao.shared.AuthUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
public class AdminController {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public record AdminUserView(UserView user, boolean active, Instant createdAt, String acquisitionSource) {
    }

    public record ActiveRequest(boolean active) {
    }

    private final KpiService kpis;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final AuthService authService;

    public AdminController(KpiService kpis, UserRepository users, RefreshTokenRepository refreshTokens,
                           AuditService audit, AuthService authService) {
        this.kpis = kpis;
        this.authService = authService;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    /** AC-010-01. Por defecto, los últimos 30 días (duración del piloto). */
    @GetMapping("/api/admin/kpis")
    public KpiService.KpiReport kpis(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(BOGOTA) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("Rango de fechas inválido");
        }
        return kpis.report(start.atStartOfDay(BOGOTA).toInstant(), end.plusDays(1).atStartOfDay(BOGOTA).toInstant());
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserView> users() {
        return users.findTop200ByOrderByCreatedAtDesc().stream()
                .map(u -> new AdminUserView(UserView.of(u), u.isActive(), u.getCreatedAt(), u.getAcquisitionSource()))
                .toList();
    }

    /** AC-001-11. */
    @PostMapping("/api/admin/users/{id}/reset-password")
    public AuthDtos.TemporaryPasswordResponse resetPassword(@PathVariable("id") UUID id) {
        AuthUser admin = AuthUser.current();
        if (admin.id().equals(id)) {
            throw ApiException.badRequest("Cambia tu propia contraseña desde tu perfil");
        }
        String temporary = authService.resetPassword(id);
        audit.record(admin.id(), "USER_PASSWORD_RESET", "user", id, null);
        return new AuthDtos.TemporaryPasswordResponse(temporary);
    }

    @PostMapping("/api/admin/users/{id}/active")
    @Transactional
    public AdminUserView setActive(@PathVariable("id") UUID id, @RequestBody ActiveRequest request) {
        AuthUser admin = AuthUser.current();
        if (admin.id().equals(id)) {
            throw ApiException.badRequest("No puedes desactivar tu propia cuenta");
        }
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
        user.setActive(request.active());
        if (!request.active()) {
            refreshTokens.revokeAllForUser(id);
        }
        audit.record(admin.id(), request.active() ? "USER_ACTIVATED" : "USER_DEACTIVATED", "user", id, null);
        return new AdminUserView(UserView.of(user), user.isActive(), user.getCreatedAt(), user.getAcquisitionSource());
    }
}
