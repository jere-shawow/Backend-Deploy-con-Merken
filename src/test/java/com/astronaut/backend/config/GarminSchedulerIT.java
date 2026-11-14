package com.astronaut.backend.config;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.GarminLink;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.astronaut.backend.support.FakeGarminServer;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * La tarea programada real (sin llamar a syncAll a mano): con el intervalo por
 * defecto de 5 s, un astronauta vinculado recibe el pulso de Garmin solo.
 */
@SpringBootTest(properties = {
        "garmin.sync.enabled=true",
        // base propia: el planificador no debe tocar los datos de otras pruebas
        "spring.datasource.url=jdbc:h2:mem:scheduler-it;DB_CLOSE_DELAY=-1"
})
@DirtiesContext // cierra el contexto (y detiene el planificador) al terminar
@ActiveProfiles("test")
class GarminSchedulerIT {

    private static final FakeGarminServer GARMIN = new FakeGarminServer();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("garmin.base-url", GARMIN::baseUrl);
    }

    @AfterAll
    static void stop() {
        GARMIN.close();
    }

    @Autowired private AstronautRepository astronauts;
    @Autowired private GarminLinkRepository links;

    @Test
    @DisplayName("el planificador sincroniza solo cada 'Refrescar datos' segundos")
    void schedulerSyncsAutomatically() {
        GARMIN.heartRate(104);
        Astronaut a = TestData.astronaut("Programado");
        a.setGarminLinked(true);
        Long id = astronauts.save(a).getId();
        links.save(new GarminLink(id, FakeGarminServer.VALID_KEY));

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    Astronaut reloaded = astronauts.findById(id).orElseThrow();
                    assertThat(reloaded.getHeartRate()).isEqualTo(104);
                    assertThat(reloaded.getGarminLastSync()).isNotNull();
                });

        // y sigue corriendo: un cambio en Garmin aparece en la siguiente vuelta
        GARMIN.heartRate(58);
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() ->
                        assertThat(astronauts.findById(id).orElseThrow().getHeartRate()).isEqualTo(58));
    }
}
