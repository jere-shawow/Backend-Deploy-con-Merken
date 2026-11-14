package com.astronaut.backend.controller;

import com.astronaut.backend.model.AppSettings;
import com.astronaut.backend.service.AppSettingsService;
import jakarta.validation.Valid;
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
    public AppSettings update(@Valid @RequestBody AppSettings settings) {
        return service.update(settings);
    }
}
