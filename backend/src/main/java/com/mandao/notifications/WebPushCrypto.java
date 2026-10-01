package com.mandao.notifications;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push sin dependencias externas, solo con la criptografía del JDK:
 * cifrado de contenido aes128gcm (RFC 8291) y cabecera VAPID con JWT ES256 (RFC 8292).
 */
public final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    public record Vapid(ECPublicKey publicKey, ECPrivateKey privateKey) {
        public String publicKeyBase64() {
            return b64(encodePoint(publicKey));
        }
    }

    // ------------------------------------------------------------------ llaves

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Vapid vapid(String publicKeyB64, String privateKeyB64) {
        try {
            ECPublicKey pub = decodePublicKey(b64d(publicKeyB64));
            BigInteger d = new BigInteger(1, b64d(privateKeyB64));
            ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(d, P256));
            return new Vapid(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Llaves VAPID inválidas", e);
        }
    }

    public static Vapid vapid(KeyPair pair) {
        return new Vapid((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());
    }

    /** Llave privada como 32 bytes (base64url), el formato que usan las herramientas de Web Push. */
    public static String privateKeyBase64(ECPrivateKey key) {
        return b64(unsigned(key.getS(), 32));
    }

    /** Punto sin comprimir: 0x04 || X(32) || Y(32). */
    public static byte[] encodePoint(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(unsigned(key.getW().getAffineX(), 32), 0, out, 1, 32);
        System.arraycopy(unsigned(key.getW().getAffineY(), 32), 0, out, 33, 32);
        return out;
    }

    public static ECPublicKey decodePublicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 0x04) {
            throw new GeneralSecurityException("Llave pública P-256 inválida");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    // ------------------------------------------------------------------ RFC 8291

    /** Cifra el mensaje para el navegador dueño de {@code p256dh} / {@code auth}. Devuelve el cuerpo HTTP. */
    public static byte[] encrypt(byte[] plaintext, String p256dhB64, String authB64) {
        return encrypt(plaintext, b64d(p256dhB64), b64d(authB64), generateKeyPair(), randomBytes(16));
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt) {
        try {
            if (plaintext.length > RECORD_SIZE - 17 - 86) {
                throw new IllegalArgumentException("Mensaje push demasiado grande");
            }
            byte[] asPublic = encodePoint((ECPublicKey) ephemeral.getPublic());
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemeral.getPrivate());
            ka.doPhase(decodePublicKey(uaPublic), true);
            byte[] ecdhSecret = ka.generateSecret();

            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
            byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // Registro único: contenido + delimitador 0x02 (último registro), sin relleno.
            byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[]{2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el mensaje push", e);
        }
    }

    /** HKDF-SHA256 (extract + expand de un bloque). */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    // ------------------------------------------------------------------ RFC 8292

    /** Cabecera {@code Authorization: vapid t=<JWT>, k=<llave pública>} para el endpoint dado. */
    public static String vapidAuthorization(String endpoint, Vapid vapid, String subject, Instant now) {
        try {
            URI uri = URI.create(endpoint);
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond()
                    + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
            Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
            es256.initSign(vapid.privateKey());
            es256.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            String jwt = header + "." + claims + "." + b64(es256.sign());
            return "vapid t=" + jwt + ", k=" + vapid.publicKeyBase64();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar VAPID", e);
        }
    }

    // ------------------------------------------------------------------ utilidades

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    public static String b64(byte[] data) {
        return B64.encodeToString(data);
    }

    public static byte[] b64d(String s) {
        // Los navegadores a veces envían base64 estándar o con relleno.
        return B64D.decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    private static byte[] unsigned(BigInteger n, int size) {
        byte[] raw = n.toByteArray();
        if (raw.length == size) {
            return raw;
        }
        byte[] out = new byte[size];
        int copy = Math.min(raw.length, size);
        System.arraycopy(raw, raw.length - copy, out, size - copy, copy);
        return out;
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
