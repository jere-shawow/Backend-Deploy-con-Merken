package com.astronaut.backend.reports.model;

import java.time.Instant;

/**
 * Comentario del informe: una notificación entregada en el día, con su hora.
 */
public record ReportComment(
        Long notificationId,
        Instant time,
        Long astronautId,
        String astronautName,
        String parameter,
        String level,
        Double value,
        String title,
        String body) {
}
