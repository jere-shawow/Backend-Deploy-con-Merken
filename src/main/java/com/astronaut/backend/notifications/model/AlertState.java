package com.astronaut.backend.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Estado de alerta activo de un astronauta en un parámetro: sirve para repetir
 * la notificación cada X minutos y no duplicarla tras un reinicio.
 * Se borra cuando el valor vuelve a un nivel no crítico.
 */
@Entity
@Table(name = "notification_alert_states",
       uniqueConstraints = @UniqueConstraint(name = "uk_alert_state", columnNames = {"astronaut_id", "health_parameter"}))
public class AlertState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "astronaut_id", nullable = false)
    private Long astronautId;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_parameter", nullable = false, length = 20)
    private HealthParameter parameter;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", nullable = false, length = 20)
    private AlertLevel level;

    @Column(name = "last_notified_at", nullable = false)
    private Instant lastNotifiedAt;

    public AlertState() {
    }

    public AlertState(Long astronautId, HealthParameter parameter, AlertLevel level, Instant lastNotifiedAt) {
        this.astronautId = astronautId;
        this.parameter = parameter;
        this.level = level;
        this.lastNotifiedAt = lastNotifiedAt;
    }

    public Long getId() { return id; }
    public Long getAstronautId() { return astronautId; }
    public HealthParameter getParameter() { return parameter; }
    public AlertLevel getLevel() { return level; }
    public void setLevel(AlertLevel level) { this.level = level; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
}
