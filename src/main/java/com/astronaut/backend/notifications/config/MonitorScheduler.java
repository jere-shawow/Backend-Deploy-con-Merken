package com.astronaut.backend.notifications.config;

import com.astronaut.backend.notifications.service.MonitoringService;
import com.astronaut.backend.notifications.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * - Revisa los niveles cada "notifications.poll-seconds" (10 s por defecto).
 * - Cada hora elimina las notificaciones vencidas (más de 24 h).
 */
@Component
@ConditionalOnProperty(name = "notifications.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class MonitorScheduler {

    private final MonitoringService monitoringService;
    private final NotificationService notificationService;

    public MonitorScheduler(MonitoringService monitoringService, NotificationService notificationService) {
        this.monitoringService = monitoringService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${notifications.poll-seconds:10}", initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void monitor() {
        monitoringService.runCycle();
    }

    @Scheduled(fixedRate = 60, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void purgeExpired() {
        notificationService.purgeExpired();
    }
}
