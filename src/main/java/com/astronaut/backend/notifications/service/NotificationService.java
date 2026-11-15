package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.config.NotificationProperties;
import com.astronaut.backend.notifications.dto.NotificationRequest;
import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.astronaut.backend.notifications.model.Notification;
import com.astronaut.backend.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationProperties properties;
    private final GuideCatalog guides;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationProperties properties,
                               GuideCatalog guides, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.guides = guides;
        this.clock = clock;
    }

    // ------------------------------------------------------------- lectura

    @Transactional(readOnly = true)
    public List<Notification> search(Long astronautId, boolean unreadOnly, boolean includeExpired) {
        return repository.search(astronautId, unreadOnly, includeExpired, clock.instant());
    }

    @Transactional(readOnly = true)
    public Notification findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe una notificación con id " + id));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long astronautId) {
        return repository.countUnread(astronautId, clock.instant());
    }

    // ----------------------------------------------------------- escritura

    /** Notificación manual (cuerpo y anexo libres). */
    public Notification create(NotificationRequest request) {
        Instant now = clock.instant();
        Notification n = new Notification();
        n.setAstronautId(request.astronautId());
        n.setAstronautName(request.astronautName());
        n.setTitle(request.title() == null || request.title().isBlank() ? "Aviso" : request.title().trim());
        n.setBody(request.body().trim());
        n.setAnnex(request.annex() == null || request.annex().isBlank() ? null : request.annex().trim());
        n.setCreatedAt(now);
        Duration timeout = request.timeoutHours() == null
                ? properties.timeout() : Duration.ofHours(request.timeoutHours());
        n.setExpiresAt(now.plus(timeout));
        return repository.save(n);
    }

    /** Notificación automática por nivel muy alto / muy bajo. */
    public Notification createAlert(Long astronautId, String astronautName, HealthParameter parameter,
                                    AlertLevel level, double value) {
        Instant now = clock.instant();
        Notification n = new Notification();
        n.setAstronautId(astronautId);
        n.setAstronautName(astronautName);
        n.setParameter(parameter);
        n.setLevel(level);
        n.setMeasuredValue(value);
        n.setTitle(guides.title(parameter, level, astronautName));
        n.setBody(guides.body(parameter, level, value, astronautName, properties.repeatInterval(parameter), now));
        n.setAnnex(guides.annex(parameter, level));
        n.setCreatedAt(now);
        n.setExpiresAt(now.plus(properties.timeout()));
        Notification saved = repository.save(n);
        log.info("Notificación {}: {}", saved.getId(), saved.getTitle());
        return saved;
    }

    public Notification markRead(Long id) {
        Notification n = findById(id);
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(clock.instant());
        }
        return n;
    }

    public int markAllRead(Long astronautId) {
        return repository.markAllRead(astronautId, clock.instant());
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }

    /** Elimina las notificaciones que superaron su timeout (24 h). */
    public int purgeExpired() {
        int deleted = repository.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Se eliminaron {} notificación(es) vencidas", deleted);
        }
        return deleted;
    }
}
