package com.astronaut.backend.reports.model;

import java.time.Instant;

/** Una medición: los datos completos de un astronauta en un momento (cada 10 min). */
public record AstronautReading(Instant capturedAt, AstronautData astronaut) {
}
