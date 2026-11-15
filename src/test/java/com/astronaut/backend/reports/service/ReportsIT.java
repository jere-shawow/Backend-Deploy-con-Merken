package com.astronaut.backend.reports.service;

import com.astronaut.backend.reports.client.AstronautDataSource;
import com.astronaut.backend.reports.client.NotificationSource;
import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.NotificationData;
import com.astronaut.backend.reports.store.LocalReportStore;
import com.astronaut.backend.reports.support.FakeServices;
import com.astronaut.backend.reports.support.MutableClock;
import com.astronaut.backend.reports.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aplicación completa SIN DynamoDB (endpoint inalcanzable): debe trabajar en local,
 * avisarlo, y aun así generar informes con todos los datos y la sección de comentarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReportsIT.ReportsTestConfig.class)
class ReportsIT {

    private static final Instant START = Instant.parse("2026-11-14T15:00:00Z"); // 12:00 en Santiago
    private static final FakeServices SERVICES = new FakeServices();
    private static final Path LOCAL_DIR = createTempDir();

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("reports-it");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("reports.local-dir", LOCAL_DIR::toString);
    }

    @TestConfiguration
    static class ReportsTestConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        AstronautDataSource testAstronauts() {
            return SERVICES;
        }

        @Bean
        @Primary
        NotificationSource testNotifications() {
            return SERVICES.notificationSource();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private ReportService service;
    @Autowired private LocalReportStore local;

    @BeforeEach
    void reset() {
        SERVICES.reset();
        clock.set(START);
        local.list().forEach(m -> local.delete(m.reportId()));
        SERVICES.astronauts.add(TestData.astronaut(1, "Valentina", 70));
        SERVICES.astronauts.add(TestData.astronaut(2, "Mateo", 82));
    }

    private void notification(long id, Instant at) {
        SERVICES.notifications.add(new NotificationData(id, 1L, "Valentina", "PULSE", "VERY_HIGH", 131.0,
                "ALERTA: Pulso muy alto – Valentina", "[14-11-2026 12:00] Pulso muy alto: 131 bpm", at));
    }

    @Test
    @DisplayName("sin DynamoDB: estado LOCAL con el mensaje para la app")
    void localModeMessage() throws Exception {
        mockMvc.perform(get("/api/reports/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LOCAL"))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.message")
                        .value("No se ha podido conectar a la base de datos... trabajando de forma local"))
                .andExpect(jsonPath("$.table").value("astronaut-reports"));
    }

    @Test
    @DisplayName("cada 10 min se agregan los datos al MISMO informe del día, con comentarios del día")
    void snapshotsAccumulate() throws Exception {
        notification(100, START.minus(Duration.ofHours(1)));            // hoy 11:00
        notification(101, Instant.parse("2026-11-13T20:00:00Z"));       // ayer -> no va

        service.captureSnapshot();
        clock.advance(Duration.ofMinutes(10));
        notification(102, clock.instant());
        service.captureSnapshot();

        mockMvc.perform(get("/api/reports"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("AUTO-2026-11-14"))
                .andExpect(jsonPath("$[0].type").value("AUTOMATIC"))
                .andExpect(jsonPath("$[0].entryCount").value(4))
                .andExpect(jsonPath("$[0].astronautCount").value(2))
                .andExpect(jsonPath("$[0].commentCount").value(2))
                .andExpect(jsonPath("$[0].deleteAfter").value("2026-11-21"))
                .andExpect(jsonPath("$[0].storage").value("LOCAL"));
    }

    @Test
    @DisplayName("informe manual: mediciones con TODOS los datos, resumen y sección de comentarios con hora")
    void manualReport() throws Exception {
        notification(200, START.minus(Duration.ofMinutes(30)));
        service.captureSnapshot();
        clock.advance(Duration.ofMinutes(5));

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.report.type").value("MANUAL"))
                .andExpect(jsonPath("$.report.entryCount").value(4))
                .andExpect(jsonPath("$.readings[0].astronaut.bodyBattery").value(64))
                .andExpect(jsonPath("$.readings[0].astronaut.hrv").value(48.0))
                .andExpect(jsonPath("$.readings[0].astronaut.bloodPressure").value("118/76"))
                .andExpect(jsonPath("$.astronauts", hasSize(2)))
                .andExpect(jsonPath("$.astronauts[0].bodyBattery.last").value(64.0))
                .andExpect(jsonPath("$.astronauts[0].hrv.avg").value(48.0))
                .andExpect(jsonPath("$.astronauts[0].alerts").value(1))
                .andExpect(jsonPath("$.astronauts[0].criticalAlerts").value(1))
                .andExpect(jsonPath("$.astronauts[0].warningAlerts").value(0))
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].time").value("2026-11-14T14:30:00Z"))
                .andExpect(jsonPath("$.comments[0].title").value(containsString("Pulso muy alto")));
    }

    @Test
    @DisplayName("cada 10 min se AGREGA una medición (no se sobrescribe): 12:00 -> 36 °C, 12:10 -> 37 °C")
    void measurementsAreListedNotOverwritten() throws Exception {
        SERVICES.astronauts.clear();
        SERVICES.astronauts.add(withTemperature(1, "Valentina", 36.0));
        service.captureSnapshot();                                   // 12:00

        clock.advance(Duration.ofMinutes(10));
        SERVICES.astronauts.clear();
        SERVICES.astronauts.add(withTemperature(1, "Valentina", 37.0));
        service.captureSnapshot();                                   // 12:10

        mockMvc.perform(get("/api/reports/{id}", "AUTO-2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline", hasSize(1)))
                .andExpect(jsonPath("$.timeline[0].astronautName").value("Valentina"))
                .andExpect(jsonPath("$.timeline[0].measurements", hasSize(2)))
                .andExpect(jsonPath("$.timeline[0].measurements[0].localTime").value("12:00"))
                .andExpect(jsonPath("$.timeline[0].measurements[0].bodyTemperature").value(36.0))
                .andExpect(jsonPath("$.timeline[0].measurements[1].localTime").value("12:10"))
                .andExpect(jsonPath("$.timeline[0].measurements[1].bodyTemperature").value(37.0))
                .andExpect(jsonPath("$.readings", hasSize(2)))
                .andExpect(jsonPath("$.readings[0].astronaut.bodyTemperature").value(36.0))
                .andExpect(jsonPath("$.readings[1].astronaut.bodyTemperature").value(37.0))
                .andExpect(jsonPath("$.astronauts[0].bodyTemperature.min").value(36.0))
                .andExpect(jsonPath("$.astronauts[0].bodyTemperature.max").value(37.0))
                .andExpect(jsonPath("$.astronauts[0].bodyTemperature.last").value(37.0));
    }

    private static AstronautData withTemperature(long id, String name, double temperature) {
        AstronautData a = TestData.astronaut(id, name, 70);
        return new AstronautData(a.id(), a.astronautName(), a.spaceRadiation(), a.distanceFromEarth(),
                a.bloodPressure(), a.heartRate(), a.bloodSugar(), temperature, a.sleepHours(), a.nutrition(),
                a.oxygenInBlood(), a.bodyBattery(), a.hrv(), a.mood(), a.garminLinked(), a.garminLastSync());
    }

    @Test
    @DisplayName("CSV con secciones MEDICIONES y COMENTARIOS (hora local)")
    void csv() throws Exception {
        notification(300, START);
        service.captureSnapshot();

        mockMvc.perform(get("/api/reports/{id}/csv", "AUTO-2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MEDICIONES")))
                .andExpect(content().string(containsString("hrv_ms;body_battery")))
                .andExpect(content().string(containsString("COMENTARIOS")))
                .andExpect(content().string(containsString("12:00;Valentina;urgente;PULSE;VERY_HIGH")));
    }

    @Test
    @DisplayName("retención desde la configuración de la app: borra los informes vencidos")
    void purgeUsesAppRetention() throws Exception {
        clock.set(Instant.parse("2026-11-10T15:00:00Z"));
        service.captureSnapshot();
        clock.set(START);
        service.captureSnapshot();
        SERVICES.retentionDays = 3; // cutoff 11-11

        org.assertj.core.api.Assertions.assertThat(service.purgeExpired()).isEqualTo(1);
        mockMvc.perform(get("/api/reports"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reportDate").value("2026-11-14"));
    }

    @Test
    @DisplayName("eliminar y 404")
    void deleteAndNotFound() throws Exception {
        service.captureSnapshot();

        mockMvc.perform(delete("/api/reports/{id}", "AUTO-2026-11-14")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reports/{id}", "AUTO-2026-11-14")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/reports/{id}", "AUTO-2026-11-14")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("si no se pueden leer los astronautas, generar devuelve 503 con un mensaje claro")
    void backendDown() throws Exception {
        SERVICES.backendDown = true;

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("datos de los astronautas")));
    }
}
