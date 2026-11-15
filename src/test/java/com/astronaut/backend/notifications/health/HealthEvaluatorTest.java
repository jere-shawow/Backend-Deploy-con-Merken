package com.astronaut.backend.notifications.health;

import com.astronaut.backend.notifications.client.AstronautSnapshot;
import com.astronaut.backend.notifications.health.HealthEvaluator.Evaluation;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthEvaluatorTest {

    private final HealthEvaluator evaluator =
            new HealthEvaluator(new HealthRangeClassifier(new ObjectMapper()));

    private static AstronautSnapshot astronaut(String bp, Double oxygen, Integer bodyBattery, Double hrv,
                                               Double radiation) {
        return new AstronautSnapshot(1L, "Ana", 72, 110.0, 36.6, 8.0, radiation, bp, oxygen, bodyBattery, hrv);
    }

    @Test
    @DisplayName("presión: se evalúan sistólica y diastólica y vale la peor")
    void bloodPressureWorstComponent() {
        Evaluation e = evaluator.evaluate(HealthParameter.BLOOD_PRESSURE,
                astronaut("118/95", 98.0, 60, 50.0, 0.8)).orElseThrow();

        assertThat(e.rangeKey()).isEqualTo(HealthParameter.DIASTOLIC_KEY);
        assertThat(e.status()).isEqualTo(HealthStatus.VERY_HIGH);
        assertThat(e.severity()).isEqualTo(RangeSeverity.CRITICAL);
        assertThat(e.displayValue()).isEqualTo("118/95");
        assertThat(e.value()).isEqualTo(95.0);
    }

    @Test
    @DisplayName("presión normal y formato inválido")
    void bloodPressureNormalAndInvalid() {
        assertThat(evaluator.bloodPressure("110/75")).get()
                .extracting(Evaluation::severity).isEqualTo(RangeSeverity.OK);
        assertThat(evaluator.bloodPressure("120-80")).isEmpty();
        assertThat(evaluator.bloodPressure(null)).isEmpty();
    }

    @Test
    @DisplayName("Body Battery y HRV altos son buenos (no alertan); bajos sí")
    void highIsGoodForEnergyAndHrv() {
        AstronautSnapshot rested = astronaut("110/75", 98.0, 95, 120.0, 0.8);
        assertThat(evaluator.evaluate(HealthParameter.BODY_BATTERY, rested).orElseThrow().needsAlert()).isFalse();
        assertThat(evaluator.evaluate(HealthParameter.HRV, rested).orElseThrow().needsAlert()).isFalse();

        AstronautSnapshot tired = astronaut("110/75", 98.0, 20, 15.0, 0.8);
        assertThat(evaluator.evaluate(HealthParameter.BODY_BATTERY, tired).orElseThrow().severity())
                .isEqualTo(RangeSeverity.CRITICAL);
        assertThat(evaluator.evaluate(HealthParameter.HRV, tired).orElseThrow().severity())
                .isEqualTo(RangeSeverity.CRITICAL);
    }

    @Test
    @DisplayName("sin dato (Body Battery / HRV opcionales) no se evalúa")
    void missingValue() {
        AstronautSnapshot noGarmin = astronaut("110/75", 98.0, null, null, 0.8);
        assertThat(evaluator.evaluate(HealthParameter.BODY_BATTERY, noGarmin)).isEmpty();
        assertThat(evaluator.evaluate(HealthParameter.HRV, noGarmin)).isEmpty();
    }
}
