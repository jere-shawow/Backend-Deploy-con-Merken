package com.astronaut.backend.controller;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.service.GarminSyncService;
import com.astronaut.backend.support.AbstractIntegrationTest;
import com.astronaut.backend.support.FakeGarminServer;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vinculación Garmin de punta a punta dentro del backend:
 * controller -> servicio -> cliente HTTP -> Garmin falso -> base de datos.
 */
class GarminControllerIT extends AbstractIntegrationTest {

    @Autowired
    private GarminSyncService syncService;

    private Astronaut saved() {
        return astronautRepository.save(TestData.astronaut("Valentina"));
    }

    private String linkBody(String key) {
        return "{\"apiKey\":\"" + key + "\"}";
    }

    @Test
    @DisplayName("vincular con key válida: 200, guarda el vínculo y trae pulso y sueño de Garmin")
    void linkWithValidKey() throws Exception {
        Astronaut a = saved();
        GARMIN.heartRate(91).sleepSeconds(25_200); // 7 h

        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody(FakeGarminServer.VALID_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.apiKeyHint").value("****-key"))
                .andExpect(jsonPath("$.lastSyncAt").isNotEmpty())
                .andExpect(jsonPath("$.lastError").value(nullValue()));

        Astronaut updated = astronautRepository.findById(a.getId()).orElseThrow();
        assertThat(updated.getHeartRate()).isEqualTo(91);
        assertThat(updated.getSleepHours()).isEqualTo(7.0);
        assertThat(updated.getGarminLinked()).isTrue();
        assertThat(updated.getGarminLastSync()).isNotNull();
        assertThat(garminLinkRepository.findByAstronautId(a.getId())).isPresent();
        // regresión del bug real: el token se pide aceptando text/plain
        assertThat(GARMIN.lastTokenAccept()).contains("*/*");
    }

    @Test
    @DisplayName("la respuesta y el GET de estado nunca incluyen la API key")
    void apiKeyIsNeverReturned() throws Exception {
        Astronaut a = saved();
        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody(FakeGarminServer.VALID_KEY)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(FakeGarminServer.VALID_KEY))));

        mockMvc.perform(get("/api/astronauts/{id}/garmin", a.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(FakeGarminServer.VALID_KEY))));

        mockMvc.perform(get("/api/astronauts/{id}", a.getId()))
                .andExpect(content().string(not(containsString(FakeGarminServer.VALID_KEY))));
    }

    @Test
    @DisplayName("vincular con key inválida: 400 con mensaje claro y no queda vinculado")
    void linkWithInvalidKey() throws Exception {
        Astronaut a = saved();

        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody("GCDC_invalid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("rechazó la API key")));

        assertThat(garminLinkRepository.findByAstronautId(a.getId())).isEmpty();
        assertThat(astronautRepository.findById(a.getId()).orElseThrow().getGarminLinked()).isFalse();
    }

    @Test
    @DisplayName("vincular sin key -> 400 de validación")
    void linkWithoutKey() throws Exception {
        Astronaut a = saved();

        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.apiKey").exists());
    }

    @Test
    @DisplayName("vincular a un astronauta inexistente -> 404")
    void linkUnknownAstronaut() throws Exception {
        mockMvc.perform(put("/api/astronauts/{id}/garmin", 424242)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody(FakeGarminServer.VALID_KEY)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("si los datos fallan tras vincular, el vínculo queda y lastError lo explica")
    void linkedButDataUnavailable() throws Exception {
        Astronaut a = saved();
        GARMIN.failDataWith(503);

        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody(FakeGarminServer.VALID_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.lastError").value(containsString("503")));
    }

    @Test
    @DisplayName("sync manual: refleja el nuevo pulso de Garmin")
    void manualSync() throws Exception {
        Astronaut a = saved();
        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(linkBody(FakeGarminServer.VALID_KEY)));
        GARMIN.heartRate(121);

        mockMvc.perform(post("/api/astronauts/{id}/garmin/sync", a.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true));

        mockMvc.perform(get("/api/astronauts/{id}", a.getId()))
                .andExpect(jsonPath("$.heartRate").value(121));
    }

    @Test
    @DisplayName("sync manual sin vínculo -> 404")
    void manualSyncWithoutLink() throws Exception {
        Astronaut a = saved();

        mockMvc.perform(post("/api/astronauts/{id}/garmin/sync", a.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("syncAll (lo que corre el planificador) actualiza a todos los vinculados")
    void scheduledSyncUpdatesAll() throws Exception {
        Astronaut a = saved();
        Astronaut b = astronautRepository.save(TestData.astronaut("Mateo"));
        for (Astronaut x : new Astronaut[]{a, b}) {
            mockMvc.perform(put("/api/astronauts/{id}/garmin", x.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(linkBody(FakeGarminServer.VALID_KEY)));
        }
        GARMIN.heartRate(66);

        syncService.syncAll();

        assertThat(astronautRepository.findById(a.getId()).orElseThrow().getHeartRate()).isEqualTo(66);
        assertThat(astronautRepository.findById(b.getId()).orElseThrow().getHeartRate()).isEqualTo(66);
    }

    @Test
    @DisplayName("token revocado por Garmin: se renueva solo y la sincronización sigue")
    void tokenRenewal() throws Exception {
        Astronaut a = saved();
        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(linkBody(FakeGarminServer.VALID_KEY)));
        int tokensBefore = GARMIN.tokenRequests();
        GARMIN.revokeTokens();
        GARMIN.heartRate(77);

        syncService.syncAll();

        assertThat(GARMIN.tokenRequests()).isEqualTo(tokensBefore + 1);
        assertThat(astronautRepository.findById(a.getId()).orElseThrow().getHeartRate()).isEqualTo(77);
    }

    @Test
    @DisplayName("desvincular: 204, estado linked=false y deja de sincronizar")
    void unlink() throws Exception {
        Astronaut a = saved();
        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(linkBody(FakeGarminServer.VALID_KEY)));

        mockMvc.perform(delete("/api/astronauts/{id}/garmin", a.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/astronauts/{id}/garmin", a.getId()))
                .andExpect(jsonPath("$.linked").value(false));
        mockMvc.perform(get("/api/astronauts/{id}", a.getId()))
                .andExpect(jsonPath("$.garminLinked").value(false));

        int before = GARMIN.heartRateRequests();
        syncService.syncAll();
        assertThat(GARMIN.heartRateRequests()).isEqualTo(before);
    }

    @Test
    @DisplayName("eliminar al astronauta elimina su vínculo Garmin")
    void deletingAstronautRemovesLink() throws Exception {
        Astronaut a = saved();
        mockMvc.perform(put("/api/astronauts/{id}/garmin", a.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(linkBody(FakeGarminServer.VALID_KEY)));

        mockMvc.perform(delete("/api/astronauts/{id}", a.getId())).andExpect(status().isNoContent());

        assertThat(garminLinkRepository.findByAstronautId(a.getId())).isEmpty();
    }
}
