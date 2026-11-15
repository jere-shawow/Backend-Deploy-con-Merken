package com.astronaut.backend.notifications.model;

import com.astronaut.backend.notifications.client.AstronautSnapshot;

import java.util.function.Function;

/**
 * Parámetros vigilados y su repetición por defecto mientras sigan en nivel crítico.
 */
public enum HealthParameter {

    PULSE("pulse", "Pulso", "bpm", 15, a -> a.heartRate() == null ? null : a.heartRate().doubleValue()),
    GLUCOSE("glucose", "Azúcar en sangre", "mg/dL", 30, AstronautSnapshot::bloodSugar),
    TEMPERATURE("temperature", "Temperatura corporal", "°C", 30, AstronautSnapshot::bodyTemperature),
    SLEEP("sleep", "Horas de sueño", "h", 1440, AstronautSnapshot::sleepHours);

    private final String key;
    private final String label;
    private final String unit;
    private final long defaultRepeatMinutes;
    private final Function<AstronautSnapshot, Double> getter;

    HealthParameter(String key, String label, String unit, long defaultRepeatMinutes,
                    Function<AstronautSnapshot, Double> getter) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.defaultRepeatMinutes = defaultRepeatMinutes;
        this.getter = getter;
    }

    /** Clave en health-ranges.json y en notifications.repeat-minutes. */
    public String key() { return key; }
    public String label() { return label; }
    public String unit() { return unit; }
    public long defaultRepeatMinutes() { return defaultRepeatMinutes; }

    public Double valueOf(AstronautSnapshot astronaut) {
        return getter.apply(astronaut);
    }
}
