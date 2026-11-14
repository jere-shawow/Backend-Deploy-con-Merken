package com.astronaut.backend.support;

import com.astronaut.backend.repository.AppSettingsRepository;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de integración: aplicación completa (controllers, servicios,
 * JPA, validación, schema.sql) sobre H2 en memoria, y Garmin reemplazado por
 * {@link FakeGarminServer}. El contexto de Spring se comparte entre clases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final FakeGarminServer GARMIN = new FakeGarminServer();

    @DynamicPropertySource
    static void garminProperties(DynamicPropertyRegistry registry) {
        registry.add("garmin.base-url", GARMIN::baseUrl);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected AstronautRepository astronautRepository;
    @Autowired protected GarminLinkRepository garminLinkRepository;
    @Autowired protected AppSettingsRepository appSettingsRepository;

    @BeforeEach
    void cleanDatabase() {
        GARMIN.reset();
        garminLinkRepository.deleteAll();
        astronautRepository.deleteAll();
        appSettingsRepository.deleteAll();
    }
}
