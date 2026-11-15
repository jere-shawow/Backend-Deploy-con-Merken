package com.astronaut.backend.notifications.client;

import com.astronaut.backend.repository.AstronautRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lee los astronautas desde MySQL (mismo proceso, sin HTTP). */
@Component
public class RepositoryAstronautSource implements AstronautSource {

    private final AstronautRepository repository;

    public RepositoryAstronautSource(AstronautRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AstronautSnapshot> findAll() {
        return repository.findAll().stream()
                .map(a -> new AstronautSnapshot(a.getId(), a.getAstronautName(), a.getHeartRate(),
                        a.getBloodSugar(), a.getBodyTemperature(), a.getSleepHours(),
                        a.getSpaceRadiation(), a.getBloodPressure(), a.getOxygenInBlood(),
                        a.getBodyBattery(), a.getHrv()))
                .toList();
    }
}
