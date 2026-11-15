package com.astronaut.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de PUT /api/settings. Sin valores por defecto: si falta el intervalo
 * de refresco la petición se rechaza con 400 (la entidad sí trae valores por defecto).
 */
public record AppSettingsRequest(
        @NotNull(message = "El intervalo de refresco es obligatorio")
        @Min(value = 5, message = "El intervalo mínimo es 5 segundos")
        @Max(value = 3600, message = "El intervalo máximo es 3600 segundos")
        Integer refreshIntervalSeconds,

        @Min(value = 1, message = "Los informes deben conservarse al menos 1 día")
        @Max(value = 365, message = "Los informes se pueden conservar como máximo 365 días")
        Integer reportRetentionDays) {
}
