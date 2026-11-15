package com.astronaut.backend.reports.client;

import com.astronaut.backend.reports.model.NotificationData;

import java.util.List;

/** Notificaciones entregadas (para la sección "Comentarios" del informe). */
public interface NotificationSource {

    /** Todas, incluidas las vencidas que aún no se borran. */
    List<NotificationData> findAll();
}
