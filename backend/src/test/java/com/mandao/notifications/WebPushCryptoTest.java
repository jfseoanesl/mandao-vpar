package com.mandao.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;

import static com.mandao.notifications.WebPushCrypto.b64;
import static com.mandao.notifications.WebPushCrypto.b64d;
import static com.mandao.notifications.WebPushCrypto.concat;
import static com.mandao.notifications.WebPushCrypto.hkdf;
import static org.assertj.core.api.Assertions.assertThat;

class WebPushCryptoTest {

    // RFC 8291, apéndice A.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    @DisplayName("AC-012-03 · cifrado aes128gcm idéntico al vector de prueba del RFC 8291")
    void rfc8291TestVector() {
        WebPushCrypto.Vapid as = WebPushCrypto.vapid(AS_PUBLIC, AS_PRIVATE);
        byte[] body = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), b64d(UA_PUBLIC), b64d(AUTH),
                new KeyPair(as.publicKey(), as.privateKey()), b64d(SALT));
        assertThat(b64(body)).isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("AC-012-03 · el navegador descifra lo que cifra el servidor (llaves aleatorias)")
    void roundTrip() throws Exception {
        KeyPair ua = WebPushCrypto.generateKeyPair();
        byte[] auth = WebPushCrypto.randomBytes(16);
        String message = "{\"notification\":{\"title\":\"Nuevo mandado cerca\"}}";
        byte[] body = WebPushCrypto.encrypt(message.getBytes(StandardCharsets.UTF_8),
                b64(WebPushCrypto.encodePoint((ECPublicKey) ua.getPublic())), b64(auth));
        assertThat(new String(decrypt(body, ua, auth), StandardCharsets.UTF_8)).isEqualTo(message);
    }

    @Test
    @DisplayName("Las llaves VAPID se serializan y se vuelven a leer")
    void vapidKeysRoundTrip() {
        WebPushCrypto.Vapid generated = WebPushCrypto.vapid(WebPushCrypto.generateKeyPair());
        String pub = generated.publicKeyBase64();
        String priv = WebPushCrypto.privateKeyBase64(generated.privateKey());
        assertThat(b64d(pub)).hasSize(65);
        assertThat(b64d(priv)).hasSize(32);
        WebPushCrypto.Vapid parsed = WebPushCrypto.vapid(pub, priv);
        assertThat(parsed.publicKey().getW()).isEqualTo(generated.publicKey().getW());
        assertThat(parsed.privateKey().getS()).isEqualTo(generated.privateKey().getS());
    }

    @Test
    @DisplayName("AC-012-03 · cabecera VAPID: JWT ES256 válido con aud = origen del endpoint")
    void vapidHeader() throws Exception {
        WebPushCrypto.Vapid vapid = WebPushCrypto.vapid(WebPushCrypto.generateKeyPair());
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc123", vapid,
                "mailto:soporte@mandao.co", now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + vapid.publicKeyBase64());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        JsonNode claims = new ObjectMapper().readTree(b64d(parts[1]));
        assertThat(claims.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("sub").asText()).isEqualTo("mailto:soporte@mandao.co");
        assertThat(claims.get("exp").asLong()).isEqualTo(now.getEpochSecond() + 12 * 3600);

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(vapid.publicKey());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(b64d(parts[2]))).isTrue();
    }

    /** Descifrado del lado del navegador (RFC 8291 §3.4), independiente del código de producción. */
    static byte[] decrypt(byte[] body, KeyPair ua, byte[] auth) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        assertThat(buf.getInt()).isEqualTo(4096);
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushCrypto.decodePublicKey(asPublic), true);
        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) ua.getPublic());
        byte[] ikm = hkdf(auth, ka.generateSecret(),
                concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic), 32);
        byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = aes.doFinal(ciphertext);
        int end = padded.length - 1;
        while (padded[end] == 0) {
            end--;
        }
        assertThat(padded[end]).isEqualTo((byte) 2);
        return Arrays.copyOf(padded, end);
    }
}
