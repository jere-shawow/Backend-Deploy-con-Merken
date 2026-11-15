package com.astronaut.backend.support;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.Mood;

/**
 * Datos de prueba reutilizables.
 */
public final class TestData {

    private TestData() {
    }

    public static Astronaut astronaut(String name) {
        return new Astronaut(name, 0.8, 408_000.0, "110/75", 72, 110.0, 36.6, 7.5, 2500, 98.0, Mood.BUENO);
    }

    /** JSON válido para POST/PUT /api/astronauts. */
    public static String astronautJson(String name) {
        return astronautJson(name, 72, 7.5);
    }

    public static String astronautJson(String name, int heartRate, double sleepHours) {
        return """
                {"astronautName":"%s","spaceRadiation":0.8,"distanceFromEarth":408000,
                 "bloodPressure":"110/75","heartRate":%d,"bloodSugar":110,"bodyTemperature":36.6,
                 "sleepHours":%s,"nutrition":2500,"oxygenInBlood":98,"mood":"BUENO"}
                """.formatted(name, heartRate, sleepHours);
    }
}
