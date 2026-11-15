package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.AlertState;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.astronaut.backend.notifications.service.AlertPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AlertPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-14T15:00:00Z");
    private static final Duration FIFTEEN = Duration.ofMinutes(15);

    private static Optional<AlertState> notifiedAt(AlertLevel level, Instant when) {
        return Optional.of(new AlertState(1L, HealthParameter.PULSE, level, when));
    }

    @Test
    @DisplayName("apenas se alcanza un nivel crítico se notifica")
    void notifiesImmediately() {
        assertThat(AlertPolicy.decide(Optional.empty(), Optional.of(AlertLevel.VERY_HIGH), T0, FIFTEEN))
                .isEqualTo(Decision.NOTIFY);
    }

    @Test
    @DisplayName("sigue crítico: espera hasta cumplir el intervalo y luego repite")
    void repeatsAfterInterval() {
        var state = notifiedAt(AlertLevel.VERY_HIGH, T0);

        assertThat(AlertPolicy.decide(state, Optional.of(AlertLevel.VERY_HIGH), T0.plusSeconds(60), FIFTEEN))
                .isEqualTo(Decision.WAIT);
        assertThat(AlertPolicy.decide(state, Optional.of(AlertLevel.VERY_HIGH), T0.plus(Duration.ofMinutes(14)), FIFTEEN))
                .isEqualTo(Decision.WAIT);
        assertThat(AlertPolicy.decide(state, Optional.of(AlertLevel.VERY_HIGH), T0.plus(FIFTEEN), FIFTEEN))
                .isEqualTo(Decision.NOTIFY);
    }

    @Test
    @DisplayName("si pasa de muy bajo a muy alto se notifica de inmediato")
    void levelChangeNotifies() {
        assertThat(AlertPolicy.decide(notifiedAt(AlertLevel.VERY_LOW, T0), Optional.of(AlertLevel.VERY_HIGH),
                T0.plusSeconds(10), FIFTEEN)).isEqualTo(Decision.NOTIFY);
    }

    @Test
    @DisplayName("al volver a un nivel no crítico se limpia el estado; sin estado no hace nada")
    void clearsWhenBackToNormal() {
        assertThat(AlertPolicy.decide(notifiedAt(AlertLevel.VERY_HIGH, T0), Optional.empty(), T0, FIFTEEN))
                .isEqualTo(Decision.CLEAR);
        assertThat(AlertPolicy.decide(Optional.empty(), Optional.empty(), T0, FIFTEEN))
                .isEqualTo(Decision.NONE);
    }
}
