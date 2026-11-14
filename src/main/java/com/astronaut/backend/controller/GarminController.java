package com.astronaut.backend.controller;

import com.astronaut.backend.dto.GarminLinkRequest;
import com.astronaut.backend.dto.GarminLinkStatus;
import com.astronaut.backend.service.GarminSyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vínculo de un astronauta con su dispositivo Garmin.
 *  GET    /api/astronauts/{id}/garmin        estado del vínculo
 *  PUT    /api/astronauts/{id}/garmin        vincular  {"apiKey": "..."}
 *  POST   /api/astronauts/{id}/garmin/sync   sincronizar ahora
 *  DELETE /api/astronauts/{id}/garmin        desvincular
 */
@RestController
@RequestMapping("/api/astronauts/{id}/garmin")
public class GarminController {

    private final GarminSyncService service;

    public GarminController(GarminSyncService service) {
        this.service = service;
    }

    @GetMapping
    public GarminLinkStatus status(@PathVariable Long id) {
        return service.status(id);
    }

    @PutMapping
    public GarminLinkStatus link(@PathVariable Long id, @Valid @RequestBody GarminLinkRequest request) {
        return service.link(id, request.apiKey());
    }

    @PostMapping("/sync")
    public GarminLinkStatus sync(@PathVariable Long id) {
        return service.syncNow(id);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(@PathVariable Long id) {
        service.unlink(id);
        return ResponseEntity.noContent().build();
    }
}
