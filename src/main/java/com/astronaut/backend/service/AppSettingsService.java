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
        AppSettings saved = repository.save(settings);
        cachedRefreshSeconds.set(saved.getRefreshIntervalSeconds());
        return saved;
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
