package com.astronaut.backend.reports.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Datos generales de un informe.
 * reportType: AUTOMATIC (se completa cada 10 min, uno por día) o MANUAL (generado desde la app).
 * expiresAtEpoch: TTL de DynamoDB (red de seguridad; la retención real la aplica el servicio).
 */
public record ReportMeta(
        String reportId,
        LocalDate reportDate,
        String reportType,
        Instant createdAt,
        Instant updatedAt,
        long readingCount,
        int astronautCount,
        int commentCount,
        long expiresAtEpoch) {

    public static final String AUTOMATIC = "AUTOMATIC";
    public static final String MANUAL = "MANUAL";
}
