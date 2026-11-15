package com.astronaut.backend.notifications.ai;

import com.astronaut.backend.analysis.AnalysisProperties;
import com.astronaut.backend.analysis.AnalysisService;
import com.astronaut.backend.analysis.AnalysisTypes.Result;
import com.astronaut.backend.analysis.AnalysisTypes.StoredRequest;
import com.astronaut.backend.exception.ServiceUnavailableException;
import com.astronaut.backend.notifications.model.Notification;
import com.astronaut.backend.notifications.repository.AlertStateRepository;
import com.astronaut.backend.notifications.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Recomendación IA (Amazon Nova Pro, vía el análisis de Gabriel) para una notificación automática.
 *
 * - Se pide AL ABRIR la notificación: el texto de Nova NO se guarda en la base de datos
 *   (regla de privacidad de docs/bedrock-analysis.md). Solo queda en memoria del proceso.
 * - Una llamada a Nova por astronauta por cada "episodio": la primera alerta o un cambio de nivel.
 *   Las repeticiones de la misma alerta reutilizan la recomendación ya generada.
 * - Envío a AWS autorizado para toda la tripulación con notifications.ai.enabled (por defecto true);
 *   además debe estar activo el análisis (BEDROCK_ANALYSIS_ENABLED=true).
 * - Usa los datos ACTUALES del astronauta (y el informe del día), no los del momento de la alerta.
 * - Las alertas no dependen de Nova: si Nova falla, la notificación y su guía siguen igual.
 */
@Service
public class AlertRecommendationService {

    private final NotificationService notifications;
    private final AlertStateRepository states;
    private final AnalysisService analysis;
    private final boolean enabled;
    /** Análisis Nova de Gabriel activo (BEDROCK_ANALYSIS_ENABLED). */
    private final boolean bedrockEnabled;
    private final Duration ttl;
    private final Clock clock;

    /** astronautId -> recomendación del episodio actual (solo memoria). */
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private record Cached(String episode, Result result, Instant generatedAt) {
    }

    /** Respuesta de GET /api/notifications/{id}/recommendation. */
    public record Recommendation(Long notificationId, Long astronautId, Instant generatedAt, boolean cached,
                                 Result analysis) {
    }

    /** Error con su estado HTTP (503 desactivado / no disponible, 429 ocupado, 400 manual). */
    public static class RecommendationException extends RuntimeException {
        private final HttpStatus status;

        public RecommendationException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    @Autowired
    public AlertRecommendationService(NotificationService notifications, AlertStateRepository states,
                                      AnalysisService analysis, AnalysisProperties analysisProperties,
                                      @Value("${notifications.ai.enabled:true}") boolean enabled,
                                      @Value("${notifications.ai.cache-hours:24}") long cacheHours,
                                      Clock clock) {
        this(notifications, states, analysis, enabled, analysisProperties.enabled(), cacheHours, clock);
    }

    /** Para pruebas: análisis Nova considerado activo. */
    AlertRecommendationService(NotificationService notifications, AlertStateRepository states,
                               AnalysisService analysis, boolean enabled, long cacheHours, Clock clock) {
        this(notifications, states, analysis, enabled, true, cacheHours, clock);
    }

    private AlertRecommendationService(NotificationService notifications, AlertStateRepository states,
                                       AnalysisService analysis, boolean enabled, boolean bedrockEnabled,
                                       long cacheHours, Clock clock) {
        this.bedrockEnabled = bedrockEnabled;
        this.notifications = notifications;
        this.states = states;
        this.analysis = analysis;
        this.enabled = enabled;
        this.ttl = Duration.ofHours(cacheHours);
        this.clock = clock;
    }

    public Recommendation forNotification(Long notificationId) {
        if (!enabled) {
            throw new RecommendationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Las recomendaciones IA están desactivadas (NOTIFICATIONS_AI_ENABLED=false)");
        }
        Notification n = notifications.findById(notificationId); // 404 si no existe
        if (n.getAstronautId() == null || n.getLevel() == null) {
            throw new RecommendationException(HttpStatus.BAD_REQUEST,
                    "Solo las notificaciones automáticas tienen recomendación IA");
        }
        if (!bedrockEnabled) {
            // Sin Nova no se consulta nada más (ni astronauta ni informes): la alerta y su guía siguen igual.
            throw new RecommendationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Recomendación IA no disponible: el análisis Nova está desactivado (BEDROCK_ANALYSIS_ENABLED=false)");
        }
        Long astronautId = n.getAstronautId();
        synchronized (locks.computeIfAbsent(astronautId, k -> new Object())) {
            String episode = episode(astronautId);
            Instant now = clock.instant();
            Cached cached = cache.get(astronautId);
            if (cached != null && cached.episode().equals(episode)
                    && cached.generatedAt().plus(ttl).isAfter(now)) {
                return new Recommendation(notificationId, astronautId, cached.generatedAt(), true, cached.result());
            }
            Result result = callNova(astronautId);
            cache.put(astronautId, new Cached(episode, result, now));
            return new Recommendation(notificationId, astronautId, now, false, result);
        }
    }

    /** Alertas vigentes del astronauta, ej. "BLOOD_PRESSURE:HIGH,PULSE:VERY_HIGH". Cambia con una alerta nueva o de nivel. */
    String episode(Long astronautId) {
        return states.findByAstronautId(astronautId).stream()
                .map(s -> s.getParameter().name() + ":" + s.getLevel().name())
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Result callNova(Long astronautId) {
        try {
            // allowRemoteAnalysis = true: el envío a AWS está autorizado para toda la tripulación
            return analysis.stored(astronautId, new StoredRequest(true, null));
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            throw new RecommendationException(status == null ? HttpStatus.SERVICE_UNAVAILABLE : status,
                    e.getStatusCode().value() == 429
                            ? "Nova está generando otra recomendación; intenta de nuevo en unos segundos"
                            : String.valueOf(e.getReason()));
        } catch (ServiceUnavailableException e) {
            throw new RecommendationException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Recomendación IA no disponible: " + e.getMessage());
        }
    }
}
