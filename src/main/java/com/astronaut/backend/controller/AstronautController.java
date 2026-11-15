package com.astronaut.backend.controller;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.garmin.GarminException;
import com.astronaut.backend.service.AstronautService;
import com.astronaut.backend.service.GarminSyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/astronauts")
public class AstronautController {

    private final AstronautService service;
    private final GarminSyncService garmin;

    public AstronautController(AstronautService service, GarminSyncService garmin) {
        this.service = service;
        this.garmin = garmin;
    }

    /** GET /api/astronauts  (opcional ?name=texto para filtrar) */
    @GetMapping
    public List<Astronaut> getAll(@RequestParam(required = false) String name) {
        if (name != null && !name.isBlank()) {
            return service.searchByName(name);
        }
        return service.findAll();
    }

    /** GET /api/astronauts/{id} */
    @GetMapping("/{id}")
    public Astronaut getById(@PathVariable Long id) {
        return service.findById(id);
    }

    /** POST /api/astronauts */
    @PostMapping
    public ResponseEntity<Astronaut> create(@Valid @RequestBody Astronaut astronaut) {
        String apiKey = astronaut.getGarminApiKey();
        Astronaut created = linkGarminIfPresent(service.create(astronaut), apiKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PUT /api/astronauts/{id} */
    @PutMapping("/{id}")
    public Astronaut update(@PathVariable Long id, @Valid @RequestBody Astronaut astronaut) {
        String apiKey = astronaut.getGarminApiKey();
        return linkGarminIfPresent(service.update(id, astronaut), apiKey);
    }

    /**
     * Si el JSON trae "garminApiKey" (o "apiKey"), vincula Garmin en la misma petición
     * (igual que PUT /api/astronauts/{id}/garmin). Si la key no sirve, el astronauta igual
     * queda guardado y la respuesta trae "garminLinkError".
     */
    private Astronaut linkGarminIfPresent(Astronaut saved, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return saved;
        }
        try {
            garmin.link(saved.getId(), apiKey.trim());
            return service.findById(saved.getId());
        } catch (GarminException e) {
            saved.setGarminLinkError("Astronauta guardado, pero no se pudo vincular con Garmin: " + e.getMessage());
            return saved;
        }
    }

    /** DELETE /api/astronauts/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
