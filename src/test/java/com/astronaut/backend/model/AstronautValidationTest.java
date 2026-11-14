package com.astronaut.backend.model;

import com.astronaut.backend.support.TestData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AstronautValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> invalidFields(Object bean) {
        Set<? extends ConstraintViolation<?>> violations = validator.validate(bean);
        return violations.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("un astronauta completo es válido")
    void validAstronaut() {
        assertThat(invalidFields(TestData.astronaut("Ok"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"120-80", "120/", "abc", "1200/80", ""})
    @DisplayName("presión arterial con formato inválido")
    void invalidBloodPressure(String value) {
        Astronaut a = TestData.astronaut("x");
        a.setBloodPressure(value);

        assertThat(invalidFields(a)).contains("bloodPressure");
    }

    @Test
    @DisplayName("rangos: pulso, temperatura, sueño, oxígeno y nutrición")
    void outOfRangeValues() {
        Astronaut a = TestData.astronaut("x");
        a.setHeartRate(10);
        a.setBodyTemperature(50.0);
        a.setSleepHours(25.0);
        a.setOxygenInBlood(101.0);
        a.setNutrition(-1);

        assertThat(invalidFields(a)).contains("heartRate", "bodyTemperature", "sleepHours",
                "oxygenInBlood", "nutrition");
    }

    @Test
    @DisplayName("nombre vacío y campos obligatorios nulos")
    void requiredFields() {
        Astronaut a = new Astronaut();
        a.setAstronautName(" ");

        assertThat(invalidFields(a)).contains("astronautName", "spaceRadiation", "distanceFromEarth",
                "bloodPressure", "heartRate", "bloodSugar", "bodyTemperature", "sleepHours",
                "nutrition", "oxygenInBlood", "mood");
    }

    @Test
    @DisplayName("refresco global: mínimo 5 y máximo 3600 segundos")
    void appSettingsRange() {
        AppSettings settings = new AppSettings();
        settings.setRefreshIntervalSeconds(4);
        assertThat(invalidFields(settings)).contains("refreshIntervalSeconds");

        settings.setRefreshIntervalSeconds(3601);
        assertThat(invalidFields(settings)).contains("refreshIntervalSeconds");

        settings.setRefreshIntervalSeconds(5);
        assertThat(invalidFields(settings)).isEmpty();
    }
}
