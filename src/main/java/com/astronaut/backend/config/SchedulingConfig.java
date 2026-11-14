package com.astronaut.backend.config;

import com.astronaut.backend.service.AppSettingsService;
import com.astronaut.backend.service.GarminSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;

/**
 * Tarea interna que sincroniza Garmin cada N segundos.
 *
 * N se lee de las settings globales en cada vuelta, así que si se cambia
 * "Refrescar datos" desde la app, el nuevo intervalo aplica de inmediato
 * (sin reiniciar el backend).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "garmin.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig implements SchedulingConfigurer {

    private final GarminSyncService garminSyncService;
    private final AppSettingsService settingsService;

    public SchedulingConfig(GarminSyncService garminSyncService, AppSettingsService settingsService) {
        this.garminSyncService = garminSyncService;
        this.settingsService = settingsService;
    }

    /** Próxima ejecución: N segundos después de terminar la anterior (N = settings globales). */
    Instant nextExecution(Instant lastCompletion) {
        Instant base = lastCompletion != null ? lastCompletion : Instant.now();
        return base.plusSeconds(settingsService.getRefreshIntervalSeconds());
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                garminSyncService::syncAll,
                context -> nextExecution(context.lastCompletion()));
    }
}
