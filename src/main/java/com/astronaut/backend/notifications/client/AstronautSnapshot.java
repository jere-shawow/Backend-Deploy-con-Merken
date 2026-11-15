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
        Double sleepHours) {
}
