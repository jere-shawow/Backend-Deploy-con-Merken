package com.astronaut.backend.notifications.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Crear una notificación manual: POST /api/notifications
 * timeoutHours es opcional (24 h por defecto).
 */
public record NotificationRequest(
        @NotNull(message = "El id del usuario (astronautId) es obligatorio") Long astronautId,
        @Size(max = 100) String astronautName,
        @Size(max = 150, message = "El título no puede superar 150 caracteres") String title,
        @NotBlank(message = "El cuerpo es obligatorio")
        @Size(max = 2000, message = "El cuerpo no puede superar 2000 caracteres") String body,
        @Size(max = 5000, message = "El anexo no puede superar 5000 caracteres") String annex,
        @Min(value = 1, message = "El timeout mínimo es 1 hora")
        @Max(value = 720, message = "El timeout máximo es 720 horas") Integer timeoutHours) {
}
