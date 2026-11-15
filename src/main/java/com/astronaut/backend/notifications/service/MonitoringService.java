package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.client.AstronautSource;
import com.astronaut.backend.notifications.client.AstronautSnapshot;
import com.astronaut.backend.notifications.config.NotificationProperties;
import com.astronaut.backend.notifications.health.HealthEvaluator;
import com.astronaut.backend.notifications.health.HealthEvaluator.Evaluation;
import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.health.HealthStatus;
import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.AlertState;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.astronaut.backend.notifications.repository.AlertStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Revisa los datos de todos los astronautas y genera notificaciones cuando
 * pulso, azúcar, temperatura o sueño salen del rango normal:
 *  - bajo / alto            -> ADVERTENCIA
 *  - muy bajo / muy alto    -> URGENTE (y de inmediato si empeora desde advertencia)
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final AstronautSource astronautSource;
    private final HealthRangeClassifier classifier;
    private final HealthEvaluator evaluator;
    private final AlertStateRepository stateRepository;
    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final Clock clock;

    public MonitoringService(AstronautSource astronautSource, HealthRangeClassifier classifier,
                             HealthEvaluator evaluator, AlertStateRepository stateRepository, NotificationService notificationService,
                             NotificationProperties properties, Clock clock) {
        this.astronautSource = astronautSource;
        this.classifier = classifier;
        this.evaluator = evaluator;
        this.stateRepository = stateRepository;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Un ciclo de revisión. Devuelve cuántas notificaciones se crearon. */
    @Transactional
    public synchronized int runCycle() {
        List<AstronautSnapshot> astronauts;
        try {
            astronauts = astronautSource.findAll();
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer los astronautas: {}", e.getMessage());
            return 0;
        }
        Instant now = clock.instant();
        int created = 0;
        for (AstronautSnapshot a : astronauts) {
            for (HealthParameter p : HealthParameter.values()) {
                created += evaluate(a, p, now) ? 1 : 0;
            }
        }
        removeStatesOfDeletedAstronauts(astronauts);
        return created;
    }

    private boolean evaluate(AstronautSnapshot astronaut, HealthParameter parameter, Instant now) {
        Optional<Evaluation> evaluation = notifies(parameter)
                ? evaluator.evaluate(parameter, astronaut).filter(Evaluation::needsAlert)
                : Optional.empty();
        Optional<AlertLevel> level = evaluation.flatMap(e -> alertLevel(e.status()));
        Optional<AlertState> state = stateRepository.findByAstronautIdAndParameter(astronaut.id(), parameter);
        Duration repeat = level.map(l -> properties.repeatInterval(parameter, l))
                .orElseGet(() -> properties.repeatInterval(parameter));

        switch (AlertPolicy.decide(state, level, now, repeat)) {
            case NOTIFY -> {
                notificationService.createAlert(astronaut.id(), astronaut.astronautName(), evaluation.orElseThrow(),
                        level.orElseThrow(), state.map(AlertState::getLevel).orElse(null));
                AlertState s = state.orElseGet(() -> new AlertState(astronaut.id(), parameter, level.get(), now));
                s.setLevel(level.get());
                s.setLastNotifiedAt(now);
                stateRepository.save(s);
                return true;
            }
            case CLEAR -> stateRepository.delete(state.orElseThrow());
            case WAIT, NONE -> { /* nada */ }
        }
        return false;
    }

    /** "notify": false en health-ranges.json desactiva las alertas del parámetro. */
    private boolean notifies(HealthParameter parameter) {
        String key = parameter == HealthParameter.BLOOD_PRESSURE ? HealthParameter.SYSTOLIC_KEY : parameter.key();
        return classifier.info(key).map(HealthRangeClassifier.ParameterInfo::notificationEnabled).orElse(true);
    }

    /**
     * Nivel del aviso. Solo llega aquí si el rango es warning / critical
     * (p. ej. Body Battery alto es "ok" y no avisa).
     */
    private static Optional<AlertLevel> alertLevel(HealthStatus status) {
        return switch (status) {
            case VERY_LOW -> Optional.of(AlertLevel.VERY_LOW);
            case LOW -> Optional.of(AlertLevel.LOW);
            case HIGH -> Optional.of(AlertLevel.HIGH);
            case VERY_HIGH -> Optional.of(AlertLevel.VERY_HIGH);
            case NORMAL -> Optional.empty();
        };
    }

    private void removeStatesOfDeletedAstronauts(List<AstronautSnapshot> astronauts) {
        Set<Long> ids = astronauts.stream().map(AstronautSnapshot::id).collect(Collectors.toSet());
        stateRepository.findAll().stream()
                .filter(s -> !ids.contains(s.getAstronautId()))
                .forEach(stateRepository::delete);
    }
}
