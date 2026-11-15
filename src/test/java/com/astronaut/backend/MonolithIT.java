package com.astronaut.backend;

import com.astronaut.backend.notifications.repository.AlertStateRepository;
import com.astronaut.backend.notifications.repository.NotificationRepository;
import com.astronaut.backend.notifications.service.MonitoringService;
import com.astronaut.backend.reports.service.ReportService;
import com.astronaut.backend.reports.store.LocalReportStore;
import com.astronaut.backend.support.AbstractIntegrationTest;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El monolito de punta a punta, sin servicios falsos:
 * astronauta en la base (MySQL/H2) -> monitor -> notificación (MySQL/H2)
 * -> informe con sus datos y comentarios (DynamoDB; aquí en local porque no hay AWS).
 */
class MonolithIT extends AbstractIntegrationTest {

    @Autowired private MonitoringService monitoring;
    @Autowired private ReportService reports;
    @Autowired private LocalReportStore localReports;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AlertStateRepository alertStateRepository;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        alertStateRepository.deleteAll();
        localReports.list().forEach(m -> localReports.delete(m.reportId()));
    }

    @Test
    @DisplayName("astronauta con pulso muy alto: notificación con hora y comentario en el informe del día")
    void astronautToNotificationToReport() throws Exception {
        mockMvc.perform(post("/api/astronauts").contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson("Valentina", 140, 7.5)))
                .andExpect(status().isCreated());

        assertThat(monitoring.runCycle()).isEqualTo(1);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].parameter").value("PULSE"))
                .andExpect(jsonPath("$[0].body").value(startsWith("[")));

        reports.captureSnapshot();

        mockMvc.perform(get("/api/reports"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("AUTOMATIC"))
                .andExpect(jsonPath("$[0].entryCount").value(1))
                .andExpect(jsonPath("$[0].commentCount").value(1))
                .andExpect(jsonPath("$[0].storage").value("LOCAL"));

        String id = reports.list().get(0).id();
        mockMvc.perform(get("/api/reports/{id}", id))
                .andExpect(jsonPath("$.readings[0].astronaut.astronautName").value("Valentina"))
                .andExpect(jsonPath("$.readings[0].astronaut.heartRate").value(140))
                .andExpect(jsonPath("$.comments[0].astronautName").value("Valentina"))
                .andExpect(jsonPath("$.astronauts[0].alerts").value(1));
    }

    @Test
    @DisplayName("la retención de informes sale de la configuración de la app")
    void retentionFromSettings() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshIntervalSeconds\":5,\"reportRetentionDays\":3}"))
                .andExpect(status().isOk());

        reports.captureSnapshot();

        String date = reports.list().get(0).reportDate().plusDays(3).toString();
        mockMvc.perform(get("/api/reports")).andExpect(jsonPath("$[0].deleteAfter").value(date));
    }

    @Test
    @DisplayName("estado del almacenamiento: sin AWS trabaja en local con el aviso")
    void storageStatus() throws Exception {
        mockMvc.perform(get("/api/reports/storage"))
                .andExpect(jsonPath("$.mode").value("LOCAL"))
                .andExpect(jsonPath("$.message")
                        .value("No se ha podido conectar a la base de datos... trabajando de forma local"));
    }
}
