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
        return new Astronaut(name, 0.8, 408_000.0, "120/80", 72, 90.0, 36.6, 7.5, 2500, 98.0, Mood.BUENO);
    }

    /** JSON válido para POST/PUT /api/astronauts. */
    public static String astronautJson(String name) {
        return astronautJson(name, 72, 7.5);
    }

    public static String astronautJson(String name, int heartRate, double sleepHours) {
        return """
                {"astronautName":"%s","spaceRadiation":0.8,"distanceFromEarth":408000,
                 "bloodPressure":"120/80","heartRate":%d,"bloodSugar":90,"bodyTemperature":36.6,
                 "sleepHours":%s,"nutrition":2500,"oxygenInBlood":98,"mood":"BUENO"}
                """.formatted(name, heartRate, sleepHours);
    }
}
