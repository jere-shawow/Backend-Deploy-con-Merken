package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.client.AstronautSource;
import com.astronaut.backend.notifications.client.AstronautSnapshot;
import com.astronaut.backend.notifications.config.NotificationProperties;
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
 * pulso, azúcar, temperatura o sueño están en nivel MUY ALTO o MUY BAJO.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final AstronautSource astronautSource;
    private final HealthRangeClassifier classifier;
    private final AlertStateRepository stateRepository;
    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final Clock clock;

    public MonitoringService(AstronautSource astronautSource, HealthRangeClassifier classifier,
                             AlertStateRepository stateRepository, NotificationService notificationService,
                             NotificationProperties properties, Clock clock) {
        this.astronautSource = astronautSource;
        this.classifier = classifier;
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
        Double value = parameter.valueOf(astronaut);
        Optional<AlertLevel> level = value == null ? Optional.empty() : criticalLevel(parameter, value);
        Optional<AlertState> state = stateRepository.findByAstronautIdAndParameter(astronaut.id(), parameter);
        Duration repeat = properties.repeatInterval(parameter);

        switch (AlertPolicy.decide(state, level, now, repeat)) {
            case NOTIFY -> {
                notificationService.createAlert(astronaut.id(), astronaut.astronautName(), parameter,
                        level.orElseThrow(), value);
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

    private Optional<AlertLevel> criticalLevel(HealthParameter parameter, double value) {
        Optional<HealthStatus> status = classifier.classify(parameter.key(), value);
        if (status.isEmpty()) {
            return Optional.empty();
        }
        return switch (status.get()) {
            case VERY_LOW -> Optional.of(AlertLevel.VERY_LOW);
            case VERY_HIGH -> Optional.of(AlertLevel.VERY_HIGH);
            default -> Optional.empty();
        };
    }

    private void removeStatesOfDeletedAstronauts(List<AstronautSnapshot> astronauts) {
        Set<Long> ids = astronauts.stream().map(AstronautSnapshot::id).collect(Collectors.toSet());
        stateRepository.findAll().stream()
                .filter(s -> !ids.contains(s.getAstronautId()))
                .forEach(stateRepository::delete);
    }
}
