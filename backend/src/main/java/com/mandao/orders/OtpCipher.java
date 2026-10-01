package com.mandao.orders;

import com.mandao.shared.MandaoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Genera y cifra (AES-256-GCM) los códigos OTP de inicio/fin (AC-005-09).
 * El cliente debe poder volver a verlos, por eso se cifran en lugar de hashearse.
 */
@Component
public class OtpCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;

    public OtpCipher(MandaoProperties props) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(props.security().otpKey().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generate() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparación en tiempo constante. */
    public boolean matches(String encoded, String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(decrypt(encoded).getBytes(StandardCharsets.UTF_8),
                candidate.trim().getBytes(StandardCharsets.UTF_8));
    }
}
