package com.mandao.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;
    private String fullName;
    private String phone;
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private UUID companyId;
    private Instant privacyAcceptedAt;
    private String acquisitionSource;
    private boolean active;
    private boolean mustChangePassword;
    private String termsVersion;
    private Instant termsAcceptedAt;
    private Instant createdAt;

    protected User() {
    }

    public User(String fullName, String phone, String email, String passwordHash, Role role, UUID companyId,
                String acquisitionSource) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.companyId = companyId;
        this.acquisitionSource = acquisitionSource;
        this.privacyAcceptedAt = Instant.now();
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Instant getPrivacyAcceptedAt() {
        return privacyAcceptedAt;
    }

    public String getAcquisitionSource() {
        return acquisitionSource;
    }

    public boolean isActive() {
        return active;
    }

    /** AC-001-12: aceptación de los términos y condiciones vigentes al registrarse. */
    public void acceptTerms(String version) {
        this.termsVersion = version;
        this.termsAcceptedAt = Instant.now();
    }

    /** AC-001-13: los admins operan la plataforma y no aceptan términos de usuario. */
    public boolean isTermsPending() {
        return role != Role.ADMIN && !AuthDtos.TERMS_VERSION.equals(termsVersion);
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    /** AC-001-10: cambio hecho por el propio usuario. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
    }

    /** AC-001-11: contraseña temporal asignada por el admin; debe cambiarse al entrar. */
    public void resetPassword(String temporaryHash) {
        this.passwordHash = temporaryHash;
        this.mustChangePassword = true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
