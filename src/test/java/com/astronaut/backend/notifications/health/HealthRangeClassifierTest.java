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
            "pulse, 39, VERY_LOW", "pulse, 40, LOW", "pulse, 120, HIGH", "pulse, 121, VERY_HIGH",
            "glucose, 53, VERY_LOW", "glucose, 200, VERY_HIGH", "glucose, 99.5, HIGH",
            "temperature, 34.9, VERY_LOW", "temperature, 40, VERY_HIGH",
            "sleep, 3.5, VERY_LOW", "sleep, 10.5, VERY_HIGH", "sleep, 8, NORMAL"
    })
    void classifies(String parameter, double value, HealthStatus expected) {
        assertThat(classifier.classify(parameter, value)).contains(expected);
    }

    @Test
    @DisplayName("texto del rango normal para el cuerpo de la notificación")
    void normalRange() {
        assertThat(classifier.normalRange("pulse")).isEqualTo("60 – 100");
        assertThat(classifier.normalRange("temperature")).isEqualTo("36 – 37,4");
        assertThat(classifier.normalRange("desconocido")).isEqualTo("-");
    }
}
