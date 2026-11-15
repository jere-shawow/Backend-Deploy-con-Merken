package com.astronaut.backend.reports.client;

import com.astronaut.backend.notifications.repository.NotificationRepository;
import com.astronaut.backend.reports.model.NotificationData;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lee las notificaciones desde MySQL (mismo proceso, sin HTTP). */
@Component
public class RepositoryNotificationSource implements NotificationSource {

    private final NotificationRepository repository;

    public RepositoryNotificationSource(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationData> findAll() {
        return repository.findAll().stream()
                .map(n -> new NotificationData(n.getId(), n.getAstronautId(), n.getAstronautName(),
                        n.getParameter() == null ? null : n.getParameter().name(),
                        n.getLevel() == null ? null : n.getLevel().name(),
                        n.getMeasuredValue(), n.getTitle(), n.getBody(), n.getCreatedAt()))
                .toList();
    }
}
