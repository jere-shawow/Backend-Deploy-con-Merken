package com.astronaut.backend.reports.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Notificación tal como la usa el informe (copia de la entidad de notifications). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationData(
        Long id,
        Long astronautId,
        String astronautName,
        String parameter,
        String level,
        Double measuredValue,
        String title,
        String body,
        Instant createdAt) {
}
