package com.astronaut.backend.reports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración "reports.*" (ver application.yml). */
@ConfigurationProperties(prefix = "reports")
public record ReportsProperties(
        String timeZone,
        Integer snapshotIntervalMinutes,
        Integer defaultRetentionDays,
        String localDir,
        Integer storageCheckSeconds,
        Dynamo dynamodb,
        String storage) {

    public record Dynamo(String table, String region, String endpoint,
                         String accessKeyId, String secretAccessKey, Boolean createTable) {
    }

    /**
     * Salida de los informes: "dynamodb" (por defecto; si no hay conexión trabaja en local)
     * o "local" (solo archivos, sin intentar AWS).
     */
    public boolean dynamoEnabled() {
        return storage == null || storage.isBlank() || !"local".equalsIgnoreCase(storage.trim());
    }

    public int retentionFallback() {
        return defaultRetentionDays == null ? 7 : defaultRetentionDays;
    }
}
