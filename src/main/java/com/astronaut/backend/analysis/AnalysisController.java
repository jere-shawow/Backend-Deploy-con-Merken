package com.astronaut.backend.analysis;

import static com.astronaut.backend.analysis.AnalysisTypes.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService service;
    public AnalysisController(AnalysisService service) { this.service=service; }
    @PostMapping("/preview")
    public org.springframework.http.ResponseEntity<Result> preview(@Valid @RequestBody Preview request) {
        return response(service.preview(request));
    }
    @PostMapping("/astronauts/{id}")
    public org.springframework.http.ResponseEntity<Result> stored(@PathVariable long id,@Valid @RequestBody StoredRequest request) {
        return response(service.stored(id,request));
    }
    private org.springframework.http.ResponseEntity<Result> response(Result result) {
        return org.springframework.http.ResponseEntity.ok()
            .cacheControl(org.springframework.http.CacheControl.noStore()).body(result);
    }
}
