package com.astronaut.backend.notifications.health;

/** Gravedad de un rango en health-ranges.json: verde / naranjo / rojo. */
public enum RangeSeverity {
    OK, WARNING, CRITICAL;

    static RangeSeverity parse(String text) {
        if (text == null) {
            return OK;
        }
        return switch (text.trim().toLowerCase()) {
            case "warning" -> WARNING;
            case "critical" -> CRITICAL;
            default -> OK;
        };
    }

    public boolean worseThan(RangeSeverity other) {
        return ordinal() > other.ordinal();
    }
}
