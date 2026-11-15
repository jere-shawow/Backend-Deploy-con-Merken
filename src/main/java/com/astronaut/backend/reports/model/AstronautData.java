package com.astronaut.backend.reports.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Todos los datos de un astronauta (copia de la entidad Astronaut de MySQL).
 * Se copia completo en cada medición del informe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AstronautData(
        Long id,
        String astronautName,
        Double spaceRadiation,
        Double distanceFromEarth,
        String bloodPressure,
        Integer heartRate,
        Double bloodSugar,
        Double bodyTemperature,
        Double sleepHours,
        Integer nutrition,
        Double oxygenInBlood,
        Integer bodyBattery,
        Double hrv,
        String mood,
        Boolean garminLinked,
        Instant garminLastSync) {
}
