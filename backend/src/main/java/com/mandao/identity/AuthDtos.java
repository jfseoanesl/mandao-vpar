package com.mandao.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    public static final String PHONE_REGEX = "^3\\d{9}$";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MSG = "Mínimo 8 caracteres con letras y números";
    /** Versión vigente de /terminos. Súbela al publicar cambios materiales (frontend: TERMS_VERSION). */
    public static final String TERMS_VERSION = "1.0";

    private AuthDtos() {
    }

    public enum SignupRole { CLIENT, COURIER }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 120) String fullName,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "Celular colombiano de 10 dígitos") String phone,
            @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotNull SignupRole role,
            @AssertTrue(message = "Debes aceptar los términos y condiciones y la política de tratamiento de datos")
            boolean acceptPrivacy,
            @Size(max = 60) String utmSource) {
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    public record UserView(UUID id, String fullName, String phone, String email, Role role, UUID companyId,
                           boolean mustChangePassword, boolean termsPending) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getFullName(), u.getPhone(), u.getEmail(), u.getRole(), u.getCompanyId(),
                    u.isMustChangePassword(), u.isTermsPending());
        }
    }

    public record AcceptTermsRequest(@NotBlank @Size(max = 10) String version) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String newPassword) {
    }

    public record TemporaryPasswordResponse(String temporaryPassword) {
    }

    public record AuthResponse(String accessToken, long expiresIn, UserView user) {
    }
}
