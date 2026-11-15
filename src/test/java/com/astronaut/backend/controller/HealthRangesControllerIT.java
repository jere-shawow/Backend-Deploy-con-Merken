package com.astronaut.backend.controller;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.support.AbstractIntegrationTest;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rangos y estado de salud para los frontends (JavaFX y web). */
class HealthRangesControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/health-ranges devuelve la definición completa")
    void ranges() throws Exception {
        mockMvc.perform(get("/api/health-ranges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.health_parameters[*].parameter").value(hasItem("body_battery")))
                .andExpect(jsonPath("$.health_parameters[?(@.parameter=='pulse')].ranges.high.severity")
                        .value(hasItem("warning")));
    }

    @Test
    @DisplayName("GET /api/astronauts/{id}/health-status: estado y gravedad de cada parámetro")
    void astronautStatus() throws Exception {
        Astronaut a = TestData.astronaut("Valentina");
        a.setHeartRate(108);           // alto -> WARNING
        a.setBloodPressure("150/95");  // muy alta -> CRITICAL
        a.setBodyBattery(95);          // muy alto pero bueno -> OK
        a = astronautRepository.save(a);

        mockMvc.perform(get("/api/astronauts/{id}/health-status", a.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.astronautName").value("Valentina"))
                .andExpect(jsonPath("$.overall").value("CRITICAL"))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='pulse')].status").value(hasItem("HIGH")))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='pulse')].severity").value(hasItem("WARNING")))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='blood_pressure')].value").value(hasItem("150/95")))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='blood_pressure')].severity").value(hasItem("CRITICAL")))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='body_battery')].severity").value(hasItem("OK")))
                .andExpect(jsonPath("$.parameters[?(@.parameter=='hrv')].value").value(hasItem((Object) null)));
    }

    @Test
    @DisplayName("GET /api/astronauts/health-status: todos los astronautas; 404 si no existe")
    void allAndNotFound() throws Exception {
        astronautRepository.save(TestData.astronaut("A"));
        astronautRepository.save(TestData.astronaut("B"));

        mockMvc.perform(get("/api/astronauts/health-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].overall").value("OK"));
        mockMvc.perform(get("/api/astronauts/{id}/health-status", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CORS: un front web en otro dominio puede llamar a la API")
    void cors() throws Exception {
        mockMvc.perform(options("/api/astronauts")
                        .header(HttpHeaders.ORIGIN, "https://front.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://front.example.com"));
    }
}
