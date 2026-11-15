package com.astronaut.backend.notifications.health;

/** Mismos niveles que el semáforo del frontend. */
public enum HealthStatus {
    VERY_LOW("very_low"),
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    VERY_HIGH("very_high");

    private final String jsonKey;

    HealthStatus(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public String jsonKey() {
        return jsonKey;
    }
}
