package com.astronaut.backend.notifications.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthRangeClassifierTest {

    private final HealthRangeClassifier classifier = new HealthRangeClassifier(new ObjectMapper());

    @ParameterizedTest(name = "{0} = {1} -> {2}")
    @CsvSource({
            // límites tomados de los rangos de la misión (health-ranges.json v2)
            "pulse, 49, VERY_LOW", "pulse, 50, LOW", "pulse, 59, LOW", "pulse, 60, NORMAL", "pulse, 100, NORMAL",
            "pulse, 101, HIGH", "pulse, 115, HIGH", "pulse, 116, VERY_HIGH",
            "glucose, 69, VERY_LOW", "glucose, 70, LOW", "glucose, 99, LOW", "glucose, 100, NORMAL",
            "glucose, 125, NORMAL", "glucose, 126, HIGH", "glucose, 199, HIGH", "glucose, 200, VERY_HIGH",
            "temperature, 34.9, VERY_LOW", "temperature, 35, LOW", "temperature, 36.0, LOW",
            "temperature, 36.1, NORMAL", "temperature, 37.2, NORMAL", "temperature, 37.3, HIGH",
            "temperature, 38.5, HIGH", "temperature, 38.6, VERY_HIGH",
            "sleep, 3.9, VERY_LOW", "sleep, 4, LOW", "sleep, 6.5, LOW", "sleep, 7, NORMAL", "sleep, 9, NORMAL",
            "sleep, 9.2, HIGH", "sleep, 11, HIGH", "sleep, 11.5, VERY_HIGH",
            "radiation, 0.05, VERY_LOW", "radiation, 0.3, LOW", "radiation, 0.8, NORMAL",
            "radiation, 1.5, HIGH", "radiation, 2.5, VERY_HIGH",
            "distance, 350, VERY_LOW", "distance, 408000, HIGH", "distance, 2000000, VERY_HIGH",
            "blood_pressure_systolic, 85, VERY_LOW", "blood_pressure_systolic, 95, LOW",
            "blood_pressure_systolic, 120, NORMAL", "blood_pressure_systolic, 130, HIGH",
            "blood_pressure_systolic, 140, VERY_HIGH",
            "blood_pressure_diastolic, 55, VERY_LOW", "blood_pressure_diastolic, 62, NORMAL",
            "blood_pressure_diastolic, 85, HIGH", "blood_pressure_diastolic, 90, VERY_HIGH",
            "oxygen, 89, VERY_LOW", "oxygen, 92, LOW", "oxygen, 97, NORMAL",
            "body_battery, 20, VERY_LOW", "body_battery, 25, VERY_LOW", "body_battery, 26, LOW",
            "body_battery, 60, NORMAL", "body_battery, 80, HIGH", "body_battery, 95, VERY_HIGH",
            "hrv, 15, VERY_LOW", "hrv, 30, LOW", "hrv, 50, NORMAL", "hrv, 90, HIGH", "hrv, 120, VERY_HIGH"
    })
    void classifies(String parameter, double value, HealthStatus expected) {
        assertThat(classifier.classify(parameter, value)).contains(expected);
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "pulse, HIGH, WARNING", "pulse, VERY_HIGH, CRITICAL", "pulse, NORMAL, OK",
            // energía / recuperación alta es buena: no alerta
            "body_battery, HIGH, OK", "body_battery, VERY_HIGH, OK", "body_battery, LOW, WARNING",
            "body_battery, VERY_LOW, CRITICAL",
            "hrv, VERY_HIGH, OK", "hrv, VERY_LOW, CRITICAL",
            // poca radiación es buena
            "radiation, VERY_LOW, OK", "radiation, HIGH, WARNING", "radiation, VERY_HIGH, CRITICAL",
            // la distancia es informativa
            "distance, VERY_HIGH, OK"
    })
    void severities(String parameter, HealthStatus status, RangeSeverity expected) {
        assertThat(classifier.severity(parameter, status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("texto del rango normal para el cuerpo de la notificación")
    void normalRange() {
        assertThat(classifier.normalRange("pulse")).isEqualTo("60 – 100");
        assertThat(classifier.normalRange("temperature")).isEqualTo("36,1 – 37,2");
        assertThat(classifier.normalRange("desconocido")).isEqualTo("-");
    }

    @Test
    @DisplayName("ánimo y nutrición no tienen rangos; la distancia no notifica")
    void noRangesForMoodAndNutrition() {
        assertThat(classifier.info("mood")).isEmpty();
        assertThat(classifier.info("nutrition")).isEmpty();
        assertThat(classifier.info("distance").orElseThrow().notifies()).isFalse();
        assertThat(classifier.info("pulse").orElseThrow().notifies()).isTrue();
    }
}
