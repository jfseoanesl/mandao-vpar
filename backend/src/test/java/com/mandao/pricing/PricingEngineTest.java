package com.mandao.pricing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingEngineTest {

    /** Tarifa versión 1 de Mandao.md. */
    static Tariff v1() {
        return new Tariff(1, 5000, 900, 10, 250, 2000, 2500, 5000, new BigDecimal("0.2250"), null);
    }

    @Test
    @DisplayName("AC-002-01 · 6 km, 2 paradas, diligencia MEDIA = $12.900 (ejemplo del documento)")
    void documentExample() {
        PricingEngine.Breakdown q = PricingEngine.quote(v1(), 6.0, 2, 0, Complexity.MEDIA);
        assertThat(q.base()).isEqualTo(5000);
        assertThat(q.distance()).isEqualTo(5400);
        assertThat(q.complexity()).isEqualTo(2500);
        assertThat(q.total()).isEqualTo(12_900);
        // Reparto de Mandao.md sección 7.A: plataforma $2.900, mandadero $10.000.
        assertThat(q.platformFee()).isEqualTo(2_900);
        assertThat(q.courierPayout()).isEqualTo(10_000);
        assertThat(q.tariffVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-002-02 · paradas extra (+$2.000 c/u sobre 2) y espera (+$250/min sobre 10)")
    void extraStopsAndWait() {
        PricingEngine.Breakdown q = PricingEngine.quote(v1(), 4.0, 4, 20, Complexity.SENCILLA);
        assertThat(q.extraStops()).isEqualTo(4000);
        assertThat(q.waitCharge()).isEqualTo(2500);
        assertThat(q.total()).isEqualTo(5000 + 3600 + 4000 + 2500);
    }

    @Test
    @DisplayName("El total se redondea hacia arriba a $100")
    void roundsUp() {
        PricingEngine.Breakdown q = PricingEngine.quote(v1(), 1.23, 2, 0, Complexity.SENCILLA);
        // 5000 + 1107 = 6107 -> 6200
        assertThat(q.total()).isEqualTo(6200);
        assertThat(q.platformFee() + q.courierPayout()).isEqualTo(q.total());
    }

    @Test
    @DisplayName("Mandado sin desplazamiento (1 parada): solo base + complejidad")
    void singleStop() {
        PricingEngine.Breakdown q = PricingEngine.quote(v1(), 0, 1, 0, Complexity.COMPLEJA);
        assertThat(q.total()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("AC-003-06 · el reparto usa la comisión vigente")
    void split() {
        PricingEngine.Split s = PricingEngine.split(18_000, new BigDecimal("0.2250"));
        assertThat(s.platformFee()).isEqualTo(4_100);
        assertThat(s.courierPayout()).isEqualTo(13_900);
    }
}
