package com.astronaut.backend.controller;

import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.service.AstronautHealthService;
import com.astronaut.backend.service.AstronautHealthService.AstronautHealth;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rangos de salud para los frontends:
 *  - GET /api/health-ranges                    definición completa (health-ranges.json)
 *  - GET /api/astronauts/health-status         estado (verde/naranjo/rojo) de todos los astronautas
 *  - GET /api/astronauts/{id}/health-status    estado de un astronauta
 */
@RestController
public class HealthRangesController {

    private final HealthRangeClassifier ranges;
    private final AstronautHealthService health;

    public HealthRangesController(HealthRangeClassifier ranges, AstronautHealthService health) {
        this.ranges = ranges;
        this.health = health;
    }

    @GetMapping("/api/health-ranges")
    public JsonNode ranges() {
        return ranges.document();
    }

    @GetMapping("/api/astronauts/health-status")
    public List<AstronautHealth> all() {
        return health.all();
    }

    @GetMapping("/api/astronauts/{id}/health-status")
    public AstronautHealth one(@PathVariable Long id) {
        return health.byId(id);
    }
}
