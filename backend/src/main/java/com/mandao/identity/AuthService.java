package com.mandao.identity;

import com.mandao.analytics.AnalyticsService;
import com.mandao.couriers.CourierProfile;
import com.mandao.couriers.CourierProfileRepository;
import com.mandao.identity.AuthDtos.AuthResponse;
import com.mandao.identity.AuthDtos.ChangePasswordRequest;
import com.mandao.identity.AuthDtos.LoginRequest;
import com.mandao.identity.AuthDtos.RegisterRequest;
import com.mandao.identity.AuthDtos.UserView;
import com.mandao.shared.ApiException;
import com.mandao.shared.MandaoProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    /** Resultado de autenticación: respuesta al cliente + refresh token en claro para la cookie. */
    public record Session(AuthResponse response, String refreshToken) {
    }

    private static final String BAD_CREDENTIALS = "Celular o contraseña incorrectos";
    // Sin caracteres ambiguos (0/O, 1/l/I) porque se dicta o se copia desde WhatsApp.
    private static final String TEMP_LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    // Hash señuelo para igualar el tiempo de respuesta cuando el celular no existe (evita enumeración).
    private final String dummyHash;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final CourierProfileRepository courierProfiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AnalyticsService analytics;
    private final Duration refreshTtl;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       CourierProfileRepository courierProfiles, PasswordEncoder passwordEncoder,
                       TokenService tokenService, AnalyticsService analytics, MandaoProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.courierProfiles = courierProfiles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.analytics = analytics;
        this.refreshTtl = Duration.ofDays(props.security().refreshTokenDays());
        this.dummyHash = passwordEncoder.encode(TokenService.randomToken());
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    @Transactional
    public Session register(RegisterRequest req) {
        if (users.existsByPhone(req.phone())) {
            throw ApiException.conflict("Este celular ya está registrado. Inicia sesión.");
        }
        Role role = req.role() == AuthDtos.SignupRole.COURIER ? Role.COURIER : Role.CLIENT;
        User newUser = new User(req.fullName().trim(), req.phone(), blankToNull(req.email()),
                passwordEncoder.encode(req.password()), role, null, blankToNull(req.utmSource()));
        newUser.acceptTerms(AuthDtos.TERMS_VERSION);
        User user = users.save(newUser);
        if (role == Role.COURIER) {
            courierProfiles.save(new CourierProfile(user.getId()));
        }
        analytics.track("signup", user.getId(), null, role.name(), user.getAcquisitionSource());
        return openSession(user, UUID.randomUUID());
    }

    @Transactional
    public Session login(LoginRequest req) {
        User user = users.findByPhone(req.phone().trim()).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(req.password(), hash);
        if (user == null || !matches || !user.isActive()) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        return openSession(user, UUID.randomUUID());
    }

    /** Rota el refresh token. Si se presenta uno ya revocado, se asume robo y se cierran todas las sesiones. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("Sesión expirada");
        }
        RefreshToken token = refreshTokens.findByTokenHash(TokenService.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada"));
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId());
            throw ApiException.unauthorized("Sesión expirada");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Sesión expirada");
        }
        User user = users.findById(token.getUserId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada"));
        token.revoke();
        return openSession(user, token.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(TokenService.sha256(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /** AC-001-10: cambia la contraseña, revoca todas las sesiones y abre una nueva. */
    @Transactional
    public Session changePassword(UUID userId, ChangePasswordRequest req) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("La contraseña actual no es correcta");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.badRequest("La nueva contraseña debe ser distinta de la actual");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(userId);
        return openSession(user, UUID.randomUUID());
    }

    /** AC-001-14: registra la aceptación de la versión vigente y emite un token sin la marca de pendiente. */
    @Transactional
    public Session acceptTerms(UUID userId, String version) {
        if (!AuthDtos.TERMS_VERSION.equals(version)) {
            throw ApiException.conflict("Los términos cambiaron. Recarga la página para leer la versión vigente.");
        }
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Sesión expirada"));
        user.acceptTerms(version);
        return openSession(user, UUID.randomUUID());
    }

    /** AC-001-11: asigna una contraseña temporal (se devuelve en claro una sola vez) y cierra sus sesiones. */
    @Transactional
    public String resetPassword(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
        String temporary = temporaryPassword();
        user.resetPassword(passwordEncoder.encode(temporary));
        refreshTokens.revokeAllForUser(userId);
        return temporary;
    }

    /** 10 caracteres con al menos una letra y un dígito (cumple PASSWORD_REGEX). */
    static String temporaryPassword() {
        String all = TEMP_LETTERS + TEMP_DIGITS;
        char[] out = new char[10];
        out[0] = TEMP_LETTERS.charAt(RANDOM.nextInt(TEMP_LETTERS.length()));
        out[1] = TEMP_DIGITS.charAt(RANDOM.nextInt(TEMP_DIGITS.length()));
        for (int i = 2; i < out.length; i++) {
            out[i] = all.charAt(RANDOM.nextInt(all.length()));
        }
        for (int i = out.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return new String(out);
    }

    public UserView me(UUID userId) {
        return users.findById(userId).map(UserView::of).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    private Session openSession(User user, UUID familyId) {
        String raw = TokenService.randomToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenService.sha256(raw), familyId,
                Instant.now().plus(refreshTtl)));
        AuthResponse response = new AuthResponse(tokenService.issueAccessToken(user),
                tokenService.accessTtlSeconds(), UserView.of(user));
        return new Session(response, raw);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
