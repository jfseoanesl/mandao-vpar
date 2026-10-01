package com.mandao.payments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementTest {

    @Test
    @DisplayName("AC-007-03 · bolsa $80.000, factura $63.700 ⇒ devolución $16.300")
    void documentExample() {
        List<Settlement.Line> lines = Settlement.compute(80_000, 63_700, 2_900, 10_000);
        assertThat(lines).containsExactly(
                new Settlement.Line(LedgerEntry.Type.REFUND, 16_300),
                new Settlement.Line(LedgerEntry.Type.COURIER_PAYOUT, 10_000),
                new Settlement.Line(LedgerEntry.Type.PLATFORM_FEE, 2_900));
    }

    @Test
    @DisplayName("Sin bolsa no hay devolución")
    void noBudget() {
        assertThat(Settlement.compute(0, 0, 2_900, 10_000))
                .extracting(Settlement.Line::type)
                .containsExactly(LedgerEntry.Type.COURIER_PAYOUT, LedgerEntry.Type.PLATFORM_FEE);
    }
}
