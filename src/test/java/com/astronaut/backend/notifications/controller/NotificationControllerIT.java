package com.astronaut.backend.notifications.controller;

import com.astronaut.backend.notifications.service.NotificationService;
import com.astronaut.backend.notifications.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    private long createManual(long astronautId, String body) throws Exception {
        String json = """
                {"astronautId":%d,"title":"Revisión","body":"%s","annex":"1. Paso uno"}
                """.formatted(astronautId, body);
        String response = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }

    @Test
    @DisplayName("crear manual: 201, ligada al usuario, con cuerpo, anexo y timeout de 24 h")
    void createManual() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"astronautId\":3,\"body\":\"Control médico a las 18:00\",\"annex\":\"Llevar resultados\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.astronautId").value(3))
                .andExpect(jsonPath("$.title").value("Aviso"))
                .andExpect(jsonPath("$.body").value("Control médico a las 18:00"))
                .andExpect(jsonPath("$.annex").value("Llevar resultados"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-14T15:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-15T15:00:00Z"))
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    @DisplayName("validación: usuario y cuerpo obligatorios")
    void validation() throws Exception {
        mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.astronautId").exists())
                .andExpect(jsonPath("$.details.body").exists());
    }

    @Test
    @DisplayName("listar por usuario, solo no leídas y contador")
    void listFilterAndCount() throws Exception {
        long a = createManual(1, "uno");
        createManual(1, "dos");
        createManual(2, "otro usuario");

        mockMvc.perform(get("/api/notifications").param("astronautId", "1"))
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(patch("/api/notifications/{id}/read", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        mockMvc.perform(get("/api/notifications").param("astronautId", "1").param("unreadOnly", "true"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("dos"));

        mockMvc.perform(get("/api/notifications/unread-count").param("astronautId", "1"))
                .andExpect(jsonPath("$.unread").value(1));
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    @DisplayName("marcar todas como leídas (de un usuario o de todos)")
    void readAll() throws Exception {
        createManual(1, "a");
        createManual(1, "b");
        createManual(2, "c");

        mockMvc.perform(post("/api/notifications/read-all").param("astronautId", "1"))
                .andExpect(jsonPath("$.updated").value(2));
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    @DisplayName("timeout: a las 24 h deja de listarse y la limpieza la elimina")
    void timeout() throws Exception {
        createManual(1, "vence");

        clock.advance(Duration.ofHours(23));
        mockMvc.perform(get("/api/notifications")).andExpect(jsonPath("$", hasSize(1)));

        clock.advance(Duration.ofHours(1));
        mockMvc.perform(get("/api/notifications")).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/notifications").param("includeExpired", "true"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));

        assertThat(notificationRepository.count()).isEqualTo(1);
        // la tarea horaria de limpieza elimina las vencidas
        assertThat(notificationService.purgeExpired()).isEqualTo(1);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("detalle, eliminar y 404")
    void getDeleteNotFound() throws Exception {
        long id = createManual(1, "borrar");

        mockMvc.perform(get("/api/notifications/{id}", id)).andExpect(jsonPath("$.body").value("borrar"));
        mockMvc.perform(delete("/api/notifications/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/{id}/read", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /evaluate revisa los niveles al instante")
    void evaluateNow() throws Exception {
        ASTRONAUTS.put(9, "Diego", 72, 40.0, 36.6, 8.0); // azúcar muy baja

        mockMvc.perform(post("/api/notifications/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        mockMvc.perform(get("/api/notifications").param("astronautId", "9"))
                .andExpect(jsonPath("$[0].parameter").value("GLUCOSE"))
                .andExpect(jsonPath("$[0].level").value("VERY_LOW"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].title").value("URGENTE: Azúcar en sangre muy baja – Diego"))
                .andExpect(jsonPath("$[0].annex").exists());
    }

    @Test
    @DisplayName("GET /{id}/recommendation: sin Bedrock activo responde 503 claro; la alerta no se ve afectada")
    void recommendationWithoutBedrock() throws Exception {
        ASTRONAUTS.put(9, "Diego", 72, 40.0, 36.6, 8.0); // azúcar muy baja
        mockMvc.perform(post("/api/notifications/evaluate")).andExpect(status().isOk());
        Long id = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/notifications/{id}/recommendation", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Recomendación IA no disponible")));
        mockMvc.perform(get("/api/notifications/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/{id}/recommendation", 999_999)).andExpect(status().isNotFound());
    }
}
