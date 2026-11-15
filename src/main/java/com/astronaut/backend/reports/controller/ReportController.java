package com.astronaut.backend.reports.controller;

import com.astronaut.backend.reports.dto.ReportDetail;
import com.astronaut.backend.reports.dto.ReportSummary;
import com.astronaut.backend.reports.service.ReportService;
import com.astronaut.backend.reports.store.StorageStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 *  GET    /api/reports                listado
 *  GET    /api/reports/{id}           detalle: resumen, mediciones y comentarios
 *  GET    /api/reports/{id}/csv       exportar
 *  POST   /api/reports/generate       informe manual con la información hasta ahora
 *  POST   /api/reports/snapshot       agregar ahora una medición al informe del día
 *  DELETE /api/reports/{id}           eliminar
 *  GET    /api/reports/storage        estado: DYNAMODB o LOCAL (con el mensaje para la app)
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportSummary> list() {
        return service.list();
    }

    @GetMapping("/storage")
    public StorageStatus storage() {
        return service.storageStatus();
    }

    @GetMapping("/{id}")
    public ReportDetail get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable String id) {
        byte[] body = ("﻿" + service.toCsv(id)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"informe-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/generate")
    public ResponseEntity<ReportDetail> generate() {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateManual());
    }

    @PostMapping("/snapshot")
    public ReportSummary snapshot() {
        return service.captureSnapshot();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
