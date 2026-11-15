package com.astronaut.backend.reports.client;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.service.AppSettingsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Lee los astronautas (MySQL) y los convierte al documento del informe,
 * que luego se guarda en DynamoDB (o en local).
 */
@Component
public class RepositoryAstronautDataSource implements AstronautDataSource {

    private final AstronautRepository repository;
    private final AppSettingsService settings;

    public RepositoryAstronautDataSource(AstronautRepository repository, AppSettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AstronautData> findAll() {
        return repository.findAll().stream().map(RepositoryAstronautDataSource::toData).toList();
    }

    @Override
    public Optional<Integer> reportRetentionDays() {
        return Optional.of(settings.getReportRetentionDays());
    }

    static AstronautData toData(Astronaut a) {
        return new AstronautData(a.getId(), a.getAstronautName(), a.getSpaceRadiation(), a.getDistanceFromEarth(),
                a.getBloodPressure(), a.getHeartRate(), a.getBloodSugar(), a.getBodyTemperature(),
                a.getSleepHours(), a.getNutrition(), a.getOxygenInBlood(), a.getBodyBattery(), a.getHrv(),
                a.getMood() == null ? null : a.getMood().name(),
                Boolean.TRUE.equals(a.getGarminLinked()), a.getGarminLastSync());
    }
}
