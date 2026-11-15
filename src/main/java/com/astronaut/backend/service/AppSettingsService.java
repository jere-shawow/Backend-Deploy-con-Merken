package com.astronaut.backend.service;

import com.astronaut.backend.model.AppSettings;
import com.astronaut.backend.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Variables globales. El intervalo de refresco se mantiene también en memoria
 * porque el planificador lo consulta en cada ejecución.
 */
@Service
public class AppSettingsService {

    private final AppSettingsRepository repository;
    private final AtomicInteger cachedRefreshSeconds = new AtomicInteger(-1);

    public AppSettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppSettings get() {
        AppSettings settings = repository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(new AppSettings()));
        cachedRefreshSeconds.set(settings.getRefreshIntervalSeconds());
        return settings;
    }

    @Transactional
    public AppSettings update(AppSettings data) {
        AppSettings settings = get();
        settings.setRefreshIntervalSeconds(data.getRefreshIntervalSeconds());
        // si no se ingresa un valor, los informes se conservan 7 días
        settings.setReportRetentionDays(data.getReportRetentionDays() == null
                ? AppSettings.DEFAULT_REPORT_RETENTION_DAYS
                : data.getReportRetentionDays());
        AppSettings saved = repository.save(settings);
        cachedRefreshSeconds.set(saved.getRefreshIntervalSeconds());
        return saved;
    }

    /** Días de retención de informes vigentes. */
    @Transactional
    public int getReportRetentionDays() {
        return get().effectiveReportRetentionDays();
    }

    /** Intervalo actual en segundos (sin ir a la base en cada consulta). */
    public int getRefreshIntervalSeconds() {
        int cached = cachedRefreshSeconds.get();
        if (cached > 0) {
            return cached;
        }
        return get().getRefreshIntervalSeconds();
    }
}
