package com.astronaut.backend.reports.dto;

import java.time.Instant;

/**
 * Una medición de un astronauta en un momento (cada 10 minutos).
 * time = instante UTC; localTime = hora local "HH:mm" para mostrar.
 */
public record Measurement(
        Instant time,
        String localTime,
        Integer heartRate,
        String bloodPressure,
        Double bloodSugar,
        Double bodyTemperature,
        Double oxygenInBlood,
        Double sleepHours,
        Integer bodyBattery,
        Double hrv,
        String mood) {
}
