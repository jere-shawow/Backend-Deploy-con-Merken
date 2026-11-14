package com.astronaut.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de PUT /api/astronauts/{id}/garmin
 */
public record GarminLinkRequest(
        @NotBlank(message = "La API key de Garmin es obligatoria") String apiKey) {
}
