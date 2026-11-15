package com.astronaut.backend.service;

import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AstronautService {

    private final AstronautRepository repository;
    private final GarminLinkRepository garminLinkRepository;

    public AstronautService(AstronautRepository repository, GarminLinkRepository garminLinkRepository) {
        this.repository = repository;
        this.garminLinkRepository = garminLinkRepository;
    }

    @Transactional(readOnly = true)
    public List<Astronaut> findAll() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Astronaut> searchByName(String name) {
        return repository.findByAstronautNameContainingIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public Astronaut findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un astronauta con id " + id));
    }

    public Astronaut create(Astronaut astronaut) {
        astronaut.setId(null);
        // el vínculo Garmin se crea con PUT /api/astronauts/{id}/garmin
        // o enviando "garminApiKey" en este mismo JSON (ver AstronautController)
        astronaut.setGarminLinked(false);
        astronaut.setGarminLastSync(null);
        return repository.save(astronaut);
    }

    public Astronaut update(Long id, Astronaut data) {
        Astronaut existing = findById(id);
        existing.setAstronautName(data.getAstronautName());
        existing.setSpaceRadiation(data.getSpaceRadiation());
        existing.setDistanceFromEarth(data.getDistanceFromEarth());
        existing.setBloodPressure(data.getBloodPressure());
        existing.setHeartRate(data.getHeartRate());
        existing.setBloodSugar(data.getBloodSugar());
        existing.setBodyTemperature(data.getBodyTemperature());
        existing.setSleepHours(data.getSleepHours());
        existing.setNutrition(data.getNutrition());
        existing.setOxygenInBlood(data.getOxygenInBlood());
        existing.setBodyBattery(data.getBodyBattery());
        existing.setHrv(data.getHrv());
        existing.setMood(data.getMood());
        return repository.save(existing);
    }

    public void delete(Long id) {
        Astronaut existing = findById(id);
        garminLinkRepository.deleteByAstronautId(id);
        repository.delete(existing);
    }
}
