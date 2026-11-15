package com.astronaut.backend.reports.support;

import com.astronaut.backend.reports.client.AstronautDataSource;
import com.astronaut.backend.reports.client.NotificationSource;
import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.NotificationData;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Astronautas, configuración y notificaciones que la prueba controla. */
public class FakeServices implements AstronautDataSource {

    public final List<AstronautData> astronauts = new CopyOnWriteArrayList<>();
    public final List<NotificationData> notifications = new CopyOnWriteArrayList<>();
    public volatile Integer retentionDays = 7;
    public volatile boolean backendDown;

    @Override
    public List<AstronautData> findAll() {
        if (backendDown) {
            throw new IllegalStateException("base de datos no disponible");
        }
        return List.copyOf(astronauts);
    }

    @Override
    public Optional<Integer> reportRetentionDays() {
        return Optional.ofNullable(retentionDays);
    }

    /** Notificaciones del día que verá el informe. */
    public NotificationSource notificationSource() {
        return () -> List.copyOf(notifications);
    }

    public void reset() {
        astronauts.clear();
        notifications.clear();
        retentionDays = 7;
        backendDown = false;
    }
}
