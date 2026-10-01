package com.mandao.identity;

import com.mandao.identity.AuthDtos.AuthResponse;
import com.mandao.identity.AuthDtos.LoginRequest;
import com.mandao.identity.AuthDtos.RegisterRequest;
import com.mandao.identity.AuthDtos.UserView;
import com.mandao.shared.AuthUser;
import com.mandao.shared.MandaoProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class AuthController {

    static final String REFRESH_COOKIE = "mandao_rt";

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService, MandaoProperties props) {
        this.authService = authService;
        this.secureCookie = props.security().cookieSecure();
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return withCookie(authService.register(request), response);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return withCookie(authService.login(request), response);
    }

    @PostMapping("/api/auth/refresh")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                HttpServletResponse response) {
        return withCookie(authService.refresh(token), response);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                       HttpServletResponse response) {
        authService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    @GetMapping("/api/me")
    public UserView me() {
        return authService.me(AuthUser.current().id());
    }

    @PutMapping("/api/me/password")
    public AuthResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,
                                       HttpServletResponse response) {
        return withCookie(authService.changePassword(AuthUser.current().id(), request), response);
    }

    @PostMapping("/api/me/terms")
    public AuthResponse acceptTerms(@Valid @RequestBody AuthDtos.AcceptTermsRequest request,
                                    HttpServletResponse response) {
        return withCookie(authService.acceptTerms(AuthUser.current().id(), request.version()), response);
    }

    private AuthResponse withCookie(AuthService.Session session, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), authService.refreshTtl()).toString());
        return session.response();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
