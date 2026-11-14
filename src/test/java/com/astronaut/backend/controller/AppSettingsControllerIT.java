package com.astronaut.backend.controller;

import com.astronaut.backend.service.AppSettingsService;
import com.astronaut.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppSettingsControllerIT extends AbstractIntegrationTest {

    @Autowired
    private AppSettingsService settingsService;

    @Test
    @DisplayName("GET devuelve el refresco por defecto (5 s)")
    void defaultSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(5));
    }

    @Test
    @DisplayName("PUT cambia el intervalo, persiste y lo ve el planificador")
    void updateSettings() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshIntervalSeconds\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(15));

        mockMvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(15));
        assertThat(settingsService.getRefreshIntervalSeconds()).isEqualTo(15);
    }

    @Test
    @DisplayName("PUT fuera de rango (< 5 o > 3600) -> 400 y no cambia nada")
    void rejectsOutOfRange() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshIntervalSeconds\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.refreshIntervalSeconds").exists());

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshIntervalSeconds\":7200}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(5));
    }

    @Test
    @DisplayName("PUT sin valor -> 400")
    void rejectsMissingValue() throws Exception {
        mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
