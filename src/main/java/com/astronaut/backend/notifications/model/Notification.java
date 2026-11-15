package com.astronaut.backend.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Notificación para un usuario (astronauta).
 *  - astronautId: id del usuario en el backend (conecta la notificación con él)
 *  - expiresAt:   timeout, 24 h después de creada
 *  - body:        de qué se trata
 *  - annex:       guía de qué hacer (opcional)
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_astronaut", columnList = "astronaut_id"),
        @Index(name = "idx_notifications_expires", columnList = "expires_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "astronaut_id", nullable = false)
    private Long astronautId;

    @Column(name = "astronaut_name", length = 100)
    private String astronautName;

    /** null en notificaciones manuales. */
    @Enumerated(EnumType.STRING)
    @Column(name = "health_parameter", length = 20)
    private HealthParameter parameter;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_level", length = 20)
    private AlertLevel level;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "annex", length = 5000)
    private String annex;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** "read" es palabra reservada en MySQL: la columna se llama is_read. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification() {
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAstronautId() { return astronautId; }
    public void setAstronautId(Long astronautId) { this.astronautId = astronautId; }
    public String getAstronautName() { return astronautName; }
    public void setAstronautName(String astronautName) { this.astronautName = astronautName; }
    public HealthParameter getParameter() { return parameter; }
    public void setParameter(HealthParameter parameter) { this.parameter = parameter; }
    public AlertLevel getLevel() { return level; }
    public void setLevel(AlertLevel level) { this.level = level; }
    public Double getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(Double measuredValue) { this.measuredValue = measuredValue; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAnnex() { return annex; }
    public void setAnnex(String annex) { this.annex = annex; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
