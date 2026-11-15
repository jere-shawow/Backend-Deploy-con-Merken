package com.astronaut.backend.controller;

import com.astronaut.backend.model.AppSettings;
import com.astronaut.backend.service.AppSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Variables globales de la aplicación.
 *  GET /api/settings
 *  PUT /api/settings   {"refreshIntervalSeconds": 5}
 */
@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {

    private final AppSettingsService service;

    public AppSettingsController(AppSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public AppSettings get() {
        return service.get();
    }

    @PutMapping
    public AppSettings update(@Valid @RequestBody UpdateSettingsRequest request) {
        AppSettings settings = new AppSettings();
        settings.setRefreshIntervalSeconds(request.refreshIntervalSeconds());
        return service.update(settings);
    }

    public record UpdateSettingsRequest(
            @NotNull(message = "El intervalo de refresco es obligatorio")
            @Min(value = 5, message = "El intervalo mínimo es 5 segundos")
            @Max(value = 3600, message = "El intervalo máximo es 3600 segundos")
            Integer refreshIntervalSeconds) {}
}
