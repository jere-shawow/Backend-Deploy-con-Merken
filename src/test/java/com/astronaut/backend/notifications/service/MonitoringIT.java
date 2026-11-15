package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.astronaut.backend.notifications.model.Notification;
import com.astronaut.backend.notifications.model.Severity;
import com.astronaut.backend.notifications.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de generación y repetición, de punta a punta:
 * astronautas (controlados por la prueba) -> monitor -> base de datos.
 */
class MonitoringIT extends AbstractIntegrationTest {

    @Autowired
    private MonitoringService monitoring;

    private List<Notification> notificationsFor(HealthParameter p) {
        return notificationRepository.findAll().stream().filter(n -> n.getParameter() == p).toList();
    }

    @Test
    @DisplayName("pulso muy alto: se notifica apenas se detecta, con cuerpo, anexo y timeout de 24 h")
    void notifiesImmediatelyWithBodyAnnexAndTimeout() {
        ASTRONAUTS.put(7, "Valentina", 131, 90.0, 36.6, 8.0);

        assertThat(monitoring.runCycle()).isEqualTo(1);

        Notification n = notificationsFor(HealthParameter.PULSE).get(0);
        assertThat(n.getAstronautId()).isEqualTo(7L);
        assertThat(n.getLevel()).isEqualTo(AlertLevel.VERY_HIGH);
        assertThat(n.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(n.getTitle()).isEqualTo("URGENTE: Pulso muy alto – Valentina");
        assertThat(n.getBody()).contains("31 bpm sobre el máximo normal", "requiere atención inmediata");
        assertThat(n.getMeasuredValue()).isEqualTo(131.0);
        assertThat(n.getBody()).contains("131 bpm", "Valentina");
        // START = 15:00 UTC = 12:00 en Santiago: la hora queda en el cuerpo
        assertThat(n.getBody()).contains("Detectado a las 12:00").startsWith("[14-11-2026 12:00]");
        assertThat(n.getAnnex()).contains("Guía – Pulso muy alto");
        assertThat(Duration.between(n.getCreatedAt(), n.getExpiresAt())).isEqualTo(Duration.ofHours(24));
        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("pulso: se repite cada 15 minutos mientras siga muy alto")
    void pulseRepeatsEvery15Minutes() {
        ASTRONAUTS.put(1, "A", 131, 90.0, 36.6, 8.0);
        monitoring.runCycle();

        clock.advance(Duration.ofMinutes(5));
        assertThat(monitoring.runCycle()).isZero();
        clock.advance(Duration.ofMinutes(9));
        assertThat(monitoring.runCycle()).isZero();
        clock.advance(Duration.ofMinutes(1));     // minuto 15
        assertThat(monitoring.runCycle()).isEqualTo(1);

        assertThat(notificationsFor(HealthParameter.PULSE)).hasSize(2);
    }

    @Test
    @DisplayName("azúcar y temperatura: se repiten cada 30 minutos")
    void glucoseAndTemperatureRepeatEvery30Minutes() {
        ASTRONAUTS.put(1, "A", 72, 250.0, 40.5, 8.0);
        assertThat(monitoring.runCycle()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(15));
        assertThat(monitoring.runCycle()).isZero();
        clock.advance(Duration.ofMinutes(15));    // minuto 30
        assertThat(monitoring.runCycle()).isEqualTo(2);

        assertThat(notificationsFor(HealthParameter.GLUCOSE)).hasSize(2);
        assertThat(notificationsFor(HealthParameter.TEMPERATURE)).hasSize(2);
    }

    @Test
    @DisplayName("sueño: una vez al día")
    void sleepOncePerDay() {
        ASTRONAUTS.put(1, "A", 72, 90.0, 36.6, 3.0);
        assertThat(monitoring.runCycle()).isEqualTo(1);

        clock.advance(Duration.ofHours(23));
        assertThat(monitoring.runCycle()).isZero();
        clock.advance(Duration.ofHours(1));
        assertThat(monitoring.runCycle()).isEqualTo(1);
    }

    @Test
    @DisplayName("si vuelve a lo normal y luego empeora otra vez, avisa de inmediato")
    void resetsWhenBackToNormal() {
        ASTRONAUTS.put(1, "A", 131, 90.0, 36.6, 8.0);
        monitoring.runCycle();

        clock.advance(Duration.ofMinutes(2));
        ASTRONAUTS.putNormal(1, "A");
        monitoring.runCycle();
        assertThat(alertStateRepository.count()).isZero();

        clock.advance(Duration.ofMinutes(2));
        ASTRONAUTS.put(1, "A", 135, 90.0, 36.6, 8.0);
        assertThat(monitoring.runCycle()).isEqualTo(1);
    }

    @Test
    @DisplayName("de muy bajo a muy alto: nueva notificación sin esperar")
    void levelChange() {
        ASTRONAUTS.put(1, "A", 35, 90.0, 36.6, 8.0);
        monitoring.runCycle();
        clock.advance(Duration.ofMinutes(1));
        ASTRONAUTS.put(1, "A", 140, 90.0, 36.6, 8.0);

        assertThat(monitoring.runCycle()).isEqualTo(1);
        assertThat(notificationsFor(HealthParameter.PULSE))
                .extracting(Notification::getLevel)
                .containsExactlyInAnyOrder(AlertLevel.VERY_LOW, AlertLevel.VERY_HIGH);
    }

    @Test
    @DisplayName("bajo / alto (naranjo): ADVERTENCIA descriptiva en los 4 parámetros")
    void warningLevelsNotify() {
        ASTRONAUTS.put(1, "Ana", 110, 150.0, 38.0, 6.0); // pulso alto, azúcar alta, temp. alta, sueño bajo

        assertThat(monitoring.runCycle()).isEqualTo(4);

        assertThat(notificationRepository.findAll())
                .allSatisfy(n -> {
                    assertThat(n.getSeverity()).isEqualTo(Severity.WARNING);
                    assertThat(n.getTitle()).startsWith("ADVERTENCIA: ");
                    assertThat(n.getBody()).contains("Aún no es crítico", "Detectado a las 12:00");
                    assertThat(n.getAnnex()).startsWith("Guía – ");
                });
        Notification pulse = notificationsFor(HealthParameter.PULSE).get(0);
        assertThat(pulse.getLevel()).isEqualTo(AlertLevel.HIGH);
        assertThat(pulse.getTitle()).isEqualTo("ADVERTENCIA: Pulso alto – Ana");
        assertThat(pulse.getBody()).contains("110 bpm", "10 bpm sobre el máximo normal", "120 bpm o más",
                "cada 30 minutos");
        Notification sleep = notificationsFor(HealthParameter.SLEEP).get(0);
        assertThat(sleep.getLevel()).isEqualTo(AlertLevel.LOW);
        assertThat(sleep.getTitle()).isEqualTo("ADVERTENCIA: Horas de sueño bajas – Ana");
        assertThat(sleep.getBody()).contains("1 h bajo el mínimo normal", "4 h o menos");
    }

    @Test
    @DisplayName("advertencia: se repite con el intervalo de advertencia (pulso cada 30 min)")
    void warningRepeatsLessOften() {
        ASTRONAUTS.put(1, "A", 110, 90.0, 36.6, 8.0);
        assertThat(monitoring.runCycle()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(15));
        assertThat(monitoring.runCycle()).isZero();
        clock.advance(Duration.ofMinutes(15));
        assertThat(monitoring.runCycle()).isEqualTo(1);
    }

    @Test
    @DisplayName("de alto a muy alto: alerta URGENTE de inmediato, indicando que empeoró")
    void escalationFromWarningToCritical() {
        ASTRONAUTS.put(1, "A", 110, 90.0, 36.6, 8.0);
        monitoring.runCycle();

        clock.advance(Duration.ofMinutes(2));
        ASTRONAUTS.put(1, "A", 135, 90.0, 36.6, 8.0);
        assertThat(monitoring.runCycle()).isEqualTo(1);

        List<Notification> pulse = notificationsFor(HealthParameter.PULSE);
        assertThat(pulse).extracting(Notification::getSeverity)
                .containsExactlyInAnyOrder(Severity.WARNING, Severity.CRITICAL);
        Notification urgent = pulse.stream().filter(n -> n.getSeverity() == Severity.CRITICAL).findFirst().orElseThrow();
        assertThat(urgent.getBody()).contains("Empeoró", "estaba alto");
    }

    @Test
    @DisplayName("de muy alto a alto: nueva advertencia indicando que mejoró")
    void deEscalation() {
        ASTRONAUTS.put(1, "A", 135, 90.0, 36.6, 8.0);
        monitoring.runCycle();

        clock.advance(Duration.ofMinutes(2));
        ASTRONAUTS.put(1, "A", 110, 90.0, 36.6, 8.0);
        assertThat(monitoring.runCycle()).isEqualTo(1);

        Notification last = notificationsFor(HealthParameter.PULSE).stream()
                .filter(n -> n.getLevel() == AlertLevel.HIGH).findFirst().orElseThrow();
        assertThat(last.getBody()).contains("Mejoró", "sigue fuera del rango normal");
    }

    @Test
    @DisplayName("cada usuario tiene sus propias alertas")
    void perUser() {
        ASTRONAUTS.put(1, "A", 131, 90.0, 36.6, 8.0);
        ASTRONAUTS.put(2, "B", 131, 90.0, 36.6, 8.0);

        assertThat(monitoring.runCycle()).isEqualTo(2);
        assertThat(notificationRepository.findAll()).extracting(Notification::getAstronautId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("si no se pueden leer los astronautas, no falla ni crea nada")
    void backendDown() {
        ASTRONAUTS.put(1, "A", 131, 90.0, 36.6, 8.0);
        ASTRONAUTS.down(true);

        assertThat(monitoring.runCycle()).isZero();
    }

    @Test
    @DisplayName("si el astronauta se elimina, su estado de alerta se limpia")
    void deletedAstronautStateIsRemoved() {
        ASTRONAUTS.put(1, "A", 131, 90.0, 36.6, 8.0);
        monitoring.runCycle();
        ASTRONAUTS.remove(1);

        monitoring.runCycle();

        assertThat(alertStateRepository.count()).isZero();
    }
}
