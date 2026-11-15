package com.astronaut.backend.notifications.support;

import com.astronaut.backend.notifications.client.AstronautSource;
import com.astronaut.backend.notifications.repository.AlertStateRepository;
import com.astronaut.backend.notifications.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

/**
 * Aplicación completa (H2 en memoria) con astronautas controlados por la prueba y reloj controlable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.ClockTestConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final Instant START = Instant.parse("2026-11-14T15:00:00Z");
    protected static final FakeAstronautSource ASTRONAUTS = new FakeAstronautSource();

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        AstronautSource testAstronauts() {
            return ASTRONAUTS;
        }
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected MutableClock clock;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected AlertStateRepository alertStateRepository;

    @BeforeEach
    void reset() {
        ASTRONAUTS.clear();
        clock.set(START);
        notificationRepository.deleteAll();
        alertStateRepository.deleteAll();
    }
}
