package com.astronaut.backend.notifications.model;

/** Niveles que generan notificación. */
public enum AlertLevel {
    VERY_LOW("muy bajo"),
    VERY_HIGH("muy alto");

    private final String label;

    AlertLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
