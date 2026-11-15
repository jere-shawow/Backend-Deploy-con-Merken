package com.astronaut.backend.notifications.model;

/**
 * Niveles que generan notificación (mismos colores que la app):
 *  - LOW / HIGH            -> advertencia (naranjo)
 *  - VERY_LOW / VERY_HIGH  -> urgente (rojo)
 */
public enum AlertLevel {
    VERY_LOW("muy bajo", Severity.CRITICAL),
    LOW("bajo", Severity.WARNING),
    HIGH("alto", Severity.WARNING),
    VERY_HIGH("muy alto", Severity.CRITICAL);

    private final String label;
    private final Severity severity;

    AlertLevel(String label, Severity severity) {
        this.label = label;
        this.severity = severity;
    }

    public String label() {
        return label;
    }

    public Severity severity() {
        return severity;
    }

    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }

    public boolean isBelowNormal() {
        return this == VERY_LOW || this == LOW;
    }
}
