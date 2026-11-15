package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GuideCatalogTest {

    private final GuideCatalog catalog =
            new GuideCatalog(new HealthRangeClassifier(new ObjectMapper()), "America/Santiago");

    @Test
    @DisplayName("hay una guía (anexo) para cada parámetro y nivel crítico")
    void everyCombinationHasAGuide() {
        for (HealthParameter p : HealthParameter.values()) {
            for (AlertLevel l : AlertLevel.values()) {
                assertThat(catalog.annex(p, l)).as(p + " " + l).isNotBlank().contains("1.");
            }
        }
    }

    @Test
    @DisplayName("el cuerpo explica valor, usuario, rango normal, repetición y la HORA en que se generó")
    void bodyDescribesTheAlert() {
        // 21:42 UTC = 18:42 en Santiago (UTC-3)
        String body = catalog.body(HealthParameter.PULSE, AlertLevel.VERY_HIGH, 131, "Valentina",
                Duration.ofMinutes(15), Instant.parse("2026-11-14T21:42:00Z"));

        assertThat(body).startsWith("[14-11-2026 18:42]");
        assertThat(body).contains("Pulso muy alto", "131 bpm", "Valentina", "60 – 100", "15 minutos",
                "Detectado a las 18:42");
        assertThat(catalog.title(HealthParameter.SLEEP, AlertLevel.VERY_LOW, "Mateo"))
                .isEqualTo("ALERTA: Horas de sueño muy bajo – Mateo");
    }

    @Test
    @DisplayName("intervalos en lenguaje natural")
    void humanDurations() {
        assertThat(GuideCatalog.human(Duration.ofMinutes(30))).isEqualTo("30 minutos");
        assertThat(GuideCatalog.human(Duration.ofMinutes(60))).isEqualTo("1 hora");
        assertThat(GuideCatalog.human(Duration.ofDays(1))).isEqualTo("1 día");
    }
}
