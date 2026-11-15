package com.astronaut.backend.notifications.ai;

import com.astronaut.backend.notifications.ai.AlertRecommendationService.Recommendation;
import com.astronaut.backend.notifications.ai.AlertRecommendationService.RecommendationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/notifications/{id}/recommendation
 * Recomendación IA (Nova Pro) para la alerta; se genera al abrirla y no se guarda en la base.
 */
@RestController
public class AlertRecommendationController {

    private final AlertRecommendationService service;

    public AlertRecommendationController(AlertRecommendationService service) {
        this.service = service;
    }

    @GetMapping("/api/notifications/{id}/recommendation")
    public ResponseEntity<Recommendation> recommendation(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.forNotification(id));
    }

    @ExceptionHandler(RecommendationException.class)
    public ResponseEntity<Map<String, Object>> handle(RecommendationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", ex.status().value());
        body.put("error", ex.status().getReasonPhrase());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.status()).body(body);
    }
}
