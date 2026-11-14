package com.astronaut.backend.controller;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.support.AbstractIntegrationTest;
import com.astronaut.backend.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AstronautControllerIT extends AbstractIntegrationTest {

    private long create(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/astronauts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson(name)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    @DisplayName("POST crea el astronauta, devuelve 201 + Location y lo persiste")
    void createAstronaut() throws Exception {
        mockMvc.perform(post("/api/astronauts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson("Camila Herrera")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/astronauts/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.astronautName").value("Camila Herrera"))
                .andExpect(jsonPath("$.nutrition").value(2500))
                .andExpect(jsonPath("$.oxygenInBlood").value(98.0))
                .andExpect(jsonPath("$.mood").value("BUENO"))
                .andExpect(jsonPath("$.garminLinked").value(false));

        assertThat(astronautRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("los campos Garmin son de solo lectura: el cliente no puede marcarse como vinculado")
    void garminFieldsAreReadOnly() throws Exception {
        String json = TestData.astronautJson("Diego").replace("\"mood\":\"BUENO\"",
                "\"mood\":\"BUENO\",\"garminLinked\":true,\"garminLastSync\":\"2026-01-01T00:00:00Z\"");

        mockMvc.perform(post("/api/astronauts").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.garminLinked").value(false))
                .andExpect(jsonPath("$.garminLastSync").value(nullValue()));
    }

    @Test
    @DisplayName("GET lista todos ordenados por id")
    void listAstronauts() throws Exception {
        create("A");
        create("B");

        mockMvc.perform(get("/api/astronauts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].astronautName").value("A"))
                .andExpect(jsonPath("$[1].astronautName").value("B"));
    }

    @Test
    @DisplayName("GET ?name= filtra sin distinguir mayúsculas")
    void searchByName() throws Exception {
        create("Valentina Rojas");
        create("Mateo Fuentes");

        mockMvc.perform(get("/api/astronauts").param("name", "VALEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].astronautName").value("Valentina Rojas"));
    }

    @Test
    @DisplayName("GET /{id} devuelve el astronauta y 404 si no existe")
    void getById() throws Exception {
        long id = create("Valentina");

        mockMvc.perform(get("/api/astronauts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.astronautName").value("Valentina"));

        mockMvc.perform(get("/api/astronauts/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("999999")));
    }

    @Test
    @DisplayName("PUT actualiza todos los datos")
    void updateAstronaut() throws Exception {
        long id = create("Valentina");

        mockMvc.perform(put("/api/astronauts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson("Valentina R.", 130, 4.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.astronautName").value("Valentina R."))
                .andExpect(jsonPath("$.heartRate").value(130))
                .andExpect(jsonPath("$.sleepHours").value(4.0));

        Astronaut stored = astronautRepository.findById(id).orElseThrow();
        assertThat(stored.getHeartRate()).isEqualTo(130);
    }

    @Test
    @DisplayName("PUT a un id inexistente -> 404")
    void updateMissing() throws Exception {
        mockMvc.perform(put("/api/astronauts/{id}", 12345)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson("X")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE elimina (204) y luego el GET da 404")
    void deleteAstronaut() throws Exception {
        long id = create("Temporal");

        mockMvc.perform(delete("/api/astronauts/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/astronauts/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/astronauts/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("validación: 400 con el detalle de cada campo inválido")
    void validationErrors() throws Exception {
        String invalid = """
                {"astronautName":"","spaceRadiation":-1,"distanceFromEarth":408000,
                 "bloodPressure":"120-80","heartRate":300,"bloodSugar":90,"bodyTemperature":36.6,
                 "sleepHours":30,"nutrition":2500,"oxygenInBlood":150,"mood":"BUENO"}
                """;

        mockMvc.perform(post("/api/astronauts").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datos inválidos"))
                .andExpect(jsonPath("$.details.astronautName").exists())
                .andExpect(jsonPath("$.details.spaceRadiation").exists())
                .andExpect(jsonPath("$.details.bloodPressure").exists())
                .andExpect(jsonPath("$.details.heartRate").exists())
                .andExpect(jsonPath("$.details.sleepHours").exists())
                .andExpect(jsonPath("$.details.oxygenInBlood").exists());

        assertThat(astronautRepository.count()).isZero();
    }

    @Test
    @DisplayName("JSON mal formado o estado de ánimo desconocido -> 400")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/api/astronauts").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/astronauts").contentType(MediaType.APPLICATION_JSON)
                        .content(TestData.astronautJson("X").replace("BUENO", "FELIZ")))
                .andExpect(status().isBadRequest());
    }
}
