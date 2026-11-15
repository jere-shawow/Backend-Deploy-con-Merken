package com.astronaut.backend.notifications.model;

/** Tipo de notificación automática según qué tan lejos está el valor del rango normal. */
public enum Severity {
    /** Bajo o alto (naranjo): conviene vigilar. */
    WARNING("Advertencia"),
    /** Muy bajo o muy alto (rojo): requiere atención inmediata. */
    CRITICAL("Urgente");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
