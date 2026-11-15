package com.astronaut.backend.notifications.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que el monitor de notificaciones necesita de cada astronauta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AstronautSnapshot(
        Long id,
        String astronautName,
        Integer heartRate,
        Double bloodSugar,
        Double bodyTemperature,
        Double sleepHours,
        Double spaceRadiation,
        String bloodPressure,
        Double oxygenInBlood,
        Integer bodyBattery,
        Double hrv) {

    /** Solo los 4 parámetros originales (el resto queda sin dato y no se evalúa). */
    public AstronautSnapshot(Long id, String astronautName, Integer heartRate, Double bloodSugar,
                             Double bodyTemperature, Double sleepHours) {
        this(id, astronautName, heartRate, bloodSugar, bodyTemperature, sleepHours, null, null, null, null, null);
    }
}
