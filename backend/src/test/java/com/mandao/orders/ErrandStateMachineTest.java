package com.mandao.orders;

import com.mandao.shared.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mandao.orders.ErrandStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrandStateMachineTest {

    @Test
    @DisplayName("Camino feliz completo")
    void happyPath() {
        assertThat(ErrandStateMachine.canTransition(SEARCHING, ASSIGNED)).isTrue();
        assertThat(ErrandStateMachine.canTransition(NEGOTIATING, ASSIGNED)).isTrue();
        assertThat(ErrandStateMachine.canTransition(ASSIGNED, GOING_TO_ORIGIN)).isTrue();
        assertThat(ErrandStateMachine.canTransition(GOING_TO_ORIGIN, IN_PROGRESS)).isTrue();
        assertThat(ErrandStateMachine.canTransition(IN_PROGRESS, DELIVERED)).isTrue();
        assertThat(ErrandStateMachine.canTransition(DELIVERED, COMPLETED)).isTrue();
        assertThat(ErrandStateMachine.canTransition(COMPLETED, RATED)).isTrue();
    }

    @Test
    @DisplayName("AC-002-11 · se cancela solo antes de iniciar la ejecución")
    void cancellation() {
        for (ErrandStatus s : ErrandStatus.CLIENT_CANCELLABLE) {
            assertThat(ErrandStateMachine.canTransition(s, CANCELLED)).as(s.name()).isTrue();
        }
        assertThat(ErrandStateMachine.canTransition(IN_PROGRESS, CANCELLED)).isFalse();
        assertThat(ErrandStateMachine.canTransition(DELIVERED, CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("No se puede saltar el OTP de inicio ni volver atrás")
    void noShortcuts() {
        assertThat(ErrandStateMachine.canTransition(ASSIGNED, IN_PROGRESS)).isFalse();
        assertThat(ErrandStateMachine.canTransition(ASSIGNED, DELIVERED)).isFalse();
        assertThat(ErrandStateMachine.canTransition(COMPLETED, SEARCHING)).isFalse();
        assertThatThrownBy(() -> ErrandStateMachine.check(RATED, COMPLETED)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Los estados finales no tienen salida (salvo COMPLETED → RATED)")
    void finalStates() {
        for (ErrandStatus to : ErrandStatus.values()) {
            assertThat(ErrandStateMachine.canTransition(CANCELLED, to)).isFalse();
            assertThat(ErrandStateMachine.canTransition(FAILED, to)).isFalse();
            assertThat(ErrandStateMachine.canTransition(RATED, to)).isFalse();
        }
    }
}
