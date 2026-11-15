package com.astronaut.backend.reports.dto;

/** Resumen de un astronauta dentro de un informe. */
public record AstronautReportStats(
        Long astronautId,
        String astronautName,
        int samples,
        MetricStats heartRate,
        MetricStats bloodSugar,
        MetricStats bodyTemperature,
        MetricStats sleepHours,
        MetricStats oxygenInBlood,
        MetricStats bodyBattery,
        MetricStats hrv,
        String lastMood,
        long alerts,
        long warningAlerts,
        long criticalAlerts) {
}
