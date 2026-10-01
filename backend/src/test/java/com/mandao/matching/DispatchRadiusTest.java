package com.mandao.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchRadiusTest {

    @Test
    @DisplayName("Radio incremental 2 → 4 → 8 km (spec 004)")
    void incrementalRadius() {
        assertThat(DispatchService.radiusKm(Duration.ofSeconds(0))).isEqualTo(2);
        assertThat(DispatchService.radiusKm(Duration.ofSeconds(59))).isEqualTo(2);
        assertThat(DispatchService.radiusKm(Duration.ofSeconds(60))).isEqualTo(4);
        assertThat(DispatchService.radiusKm(Duration.ofSeconds(119))).isEqualTo(4);
        assertThat(DispatchService.radiusKm(Duration.ofMinutes(3))).isEqualTo(8);
    }
}
