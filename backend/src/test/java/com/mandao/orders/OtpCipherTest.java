package com.mandao.orders;

import com.mandao.shared.MandaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCipherTest {

    private final OtpCipher cipher = new OtpCipher(new MandaoProperties(
            new MandaoProperties.Security("x".repeat(32), 15, 30, "test-otp-key", false, List.of()),
            null, null, null, null, null, false, null, null));

    @Test
    @DisplayName("AC-005-09 · el OTP se cifra (no se guarda en claro) y se puede verificar")
    void encryptsAndMatches() {
        String code = OtpCipher.generate();
        assertThat(code).matches("\\d{4}");
        String enc = cipher.encrypt(code);
        assertThat(enc).doesNotContain(code);
        assertThat(cipher.encrypt(code)).isNotEqualTo(enc); // IV aleatorio
        assertThat(cipher.decrypt(enc)).isEqualTo(code);
        assertThat(cipher.matches(enc, code)).isTrue();
        assertThat(cipher.matches(enc, code.equals("0000") ? "1111" : "0000")).isFalse();
        assertThat(cipher.matches(enc, null)).isFalse();
    }
}
