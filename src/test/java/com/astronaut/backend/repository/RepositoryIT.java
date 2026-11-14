package com.astronaut.backend.repository;

import com.astronaut.backend.model.AppSettings;
import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.GarminLink;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capa de persistencia: consultas derivadas y mapeo de entidades sobre H2.
 */
@DataJpaTest
@ActiveProfiles("test")
class RepositoryIT {

    @Autowired private AstronautRepository astronauts;
    @Autowired private GarminLinkRepository links;
    @Autowired private AppSettingsRepository settings;

    @Test
    @DisplayName("findAllByOrderByIdAsc devuelve en orden de creación")
    void orderedById() {
        Astronaut b = astronauts.save(TestData.astronaut("B"));
        Astronaut a = astronauts.save(TestData.astronaut("A"));

        assertThat(astronauts.findAllByOrderByIdAsc())
                .extracting(Astronaut::getId)
                .containsExactly(b.getId(), a.getId());
    }

    @Test
    @DisplayName("búsqueda por nombre parcial e insensible a mayúsculas")
    void searchByName() {
        astronauts.save(TestData.astronaut("Valentina Rojas"));
        astronauts.save(TestData.astronaut("Mateo Fuentes"));

        assertThat(astronauts.findByAstronautNameContainingIgnoreCase("ROJ"))
                .extracting(Astronaut::getAstronautName)
                .containsExactly("Valentina Rojas");
    }

    @Test
    @DisplayName("todos los campos (incluidos nutrición, oxígeno y estado Garmin) se persisten")
    void persistsAllFields() {
        Astronaut a = TestData.astronaut("Completo");
        a.setGarminLinked(true);
        a.setGarminLastSync(Instant.parse("2026-11-14T15:00:00Z"));
        Long id = astronauts.saveAndFlush(a).getId();

        Astronaut loaded = astronauts.findById(id).orElseThrow();
        assertThat(loaded.getNutrition()).isEqualTo(2500);
        assertThat(loaded.getOxygenInBlood()).isEqualTo(98.0);
        assertThat(loaded.getGarminLinked()).isTrue();
        assertThat(loaded.getGarminLastSync()).isEqualTo(Instant.parse("2026-11-14T15:00:00Z"));
    }

    @Test
    @DisplayName("GarminLink: buscar y borrar por astronauta")
    void garminLinkByAstronaut() {
        links.save(new GarminLink(10L, "GCDC_a"));
        links.save(new GarminLink(11L, "GCDC_b"));

        assertThat(links.findByAstronautId(10L)).get()
                .extracting(GarminLink::getApiKey).isEqualTo("GCDC_a");

        links.deleteByAstronautId(10L);

        assertThat(links.findByAstronautId(10L)).isEmpty();
        assertThat(links.findByAstronautId(11L)).isPresent();
    }

    @Test
    @DisplayName("AppSettings se guarda como fila única (id = 1)")
    void singletonSettings() {
        AppSettings s = new AppSettings();
        s.setRefreshIntervalSeconds(9);
        settings.save(s);
        AppSettings again = new AppSettings();
        again.setRefreshIntervalSeconds(11);
        settings.save(again);

        assertThat(settings.count()).isEqualTo(1);
        assertThat(settings.findById(AppSettings.SINGLETON_ID)).get()
                .extracting(AppSettings::getRefreshIntervalSeconds).isEqualTo(11);
    }
}
