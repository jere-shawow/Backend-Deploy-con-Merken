package com.astronaut.backend.reports.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Fila del listado de informes. */
public record ReportSummary(
        String id,
        LocalDate reportDate,
        String type,
        Instant createdAt,
        Instant updatedAt,
        long entryCount,
        long astronautCount,
        long commentCount,
        LocalDate deleteAfter,
        String storage) {
}
