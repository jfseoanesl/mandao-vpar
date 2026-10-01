package com.mandao.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mandao")
public record MandaoProperties(
        Security security,
        Storage storage,
        Routing routing,
        ServiceArea serviceArea,
        Contact contact,
        BootstrapAdmin bootstrapAdmin,
        boolean seedDemo,
        Jobs jobs,
        Push push) {

    public record Security(String jwtSecret, int accessTokenMinutes, int refreshTokenDays, String otpKey,
                           boolean cookieSecure, List<String> corsOrigins) {
    }

    public record Storage(String type, String localDir, String s3Endpoint, String s3AccessKey,
                          String s3SecretKey, String s3Bucket) {
    }

    public record Routing(String osrmUrl, boolean enabled) {
    }

    public record ServiceArea(double centerLat, double centerLng, double radiusKm) {
    }

    public record Contact(String whatsapp) {
    }

    public record BootstrapAdmin(String phone, String password, String name) {
    }

    public record Jobs(boolean enabled) {
    }

    /**
     * Web Push (spec 012). Llaves VAPID en base64url: pública de 65 bytes, privada de 32.
     * {@code allowedHosts}: servicios de push a los que el servidor puede enviar (evita SSRF con endpoints
     * arbitrarios); un valor que empieza por "." admite subdominios. {@code allowHttp} solo para pruebas.
     */
    public record Push(String publicKey, String privateKey, String subject, List<String> allowedHosts,
                       boolean allowHttp) {
    }
}
