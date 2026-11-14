package com.astronaut.backend.config;

import com.astronaut.backend.service.AppSettingsService;
import com.astronaut.backend.service.GarminSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TriggerTask;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulingConfigTest {

    private final GarminSyncService sync = mock(GarminSyncService.class);
    private final AppSettingsService settings = mock(AppSettingsService.class);
    private final SchedulingConfig config = new SchedulingConfig(sync, settings);

    @Test
    @DisplayName("la próxima ejecución es 'Refrescar datos' segundos después de la anterior")
    void nextExecutionUsesCurrentInterval() {
        when(settings.getRefreshIntervalSeconds()).thenReturn(5);
        Instant last = Instant.parse("2026-11-14T12:00:00Z");

        assertThat(config.nextExecution(last)).isEqualTo(Instant.parse("2026-11-14T12:00:05Z"));
    }

    @Test
    @DisplayName("si el intervalo cambia, la siguiente vuelta ya usa el nuevo valor")
    void intervalChangeAppliesImmediately() {
        Instant last = Instant.parse("2026-11-14T12:00:00Z");
        when(settings.getRefreshIntervalSeconds()).thenReturn(5, 60);

        assertThat(config.nextExecution(last)).isEqualTo(last.plusSeconds(5));
        assertThat(config.nextExecution(last)).isEqualTo(last.plusSeconds(60));
    }

    @Test
    @DisplayName("primera ejecución: N segundos desde ahora")
    void firstExecution() {
        when(settings.getRefreshIntervalSeconds()).thenReturn(5);
        Instant before = Instant.now();

        Instant next = config.nextExecution(null);

        assertThat(next).isAfterOrEqualTo(before.plusSeconds(5));
    }

    @Test
    @DisplayName("registra exactamente una tarea de sincronización")
    void registersOneTriggerTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        config.configureTasks(registrar);

        assertThat(registrar.getTriggerTaskList()).hasSize(1);
        TriggerTask task = registrar.getTriggerTaskList().get(0);
        task.getRunnable().run();
        org.mockito.Mockito.verify(sync).syncAll();
    }
}
