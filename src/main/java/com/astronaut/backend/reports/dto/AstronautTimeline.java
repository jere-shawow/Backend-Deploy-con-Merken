package com.astronaut.backend.reports.dto;

import java.util.List;

/**
 * Historial del día de un astronauta: todas sus mediciones en orden, sin sobrescribir.
 * Ej.: 12:00 -> 36,0 °C, 12:10 -> 37,0 °C, 12:20 -> ...
 */
public record AstronautTimeline(
        Long astronautId,
        String astronautName,
        List<Measurement> measurements) {
}
