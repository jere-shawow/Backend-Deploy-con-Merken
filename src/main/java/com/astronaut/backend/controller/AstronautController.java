package com.astronaut.backend.controller;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.service.AstronautService;
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

    public AstronautController(AstronautService service) {
        this.service = service;
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
        Astronaut created = service.create(astronaut);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** PUT /api/astronauts/{id} */
    @PutMapping("/{id}")
    public Astronaut update(@PathVariable Long id, @Valid @RequestBody Astronaut astronaut) {
        return service.update(id, astronaut);
    }

    /** DELETE /api/astronauts/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
