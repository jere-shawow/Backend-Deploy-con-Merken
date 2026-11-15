package com.astronaut.backend.reports.store;

import java.time.Instant;

/** Estado del almacenamiento que se muestra en la app. */
public record StorageStatus(
        String mode,          // DYNAMODB o LOCAL
        boolean connected,
        String message,
        String table,
        String region,
        int localReports,
        String lastError,
        Instant checkedAt) {
}
