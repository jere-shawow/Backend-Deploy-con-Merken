package com.astronaut.backend.notifications.model;

import com.astronaut.backend.notifications.client.AstronautSnapshot;

import java.util.function.Function;

/**
 * Parámetros vigilados (todos los de salud excepto el ánimo) y su repetición por defecto
 * mientras sigan en nivel URGENTE. La advertencia se repite con el doble de intervalo
 * (ver notifications.warning-repeat-minutes).
 *
 * key = clave en health-ranges.json y en notifications.repeat-minutes.
 * La presión arterial usa dos rangos (sistólica y diastólica): vale el peor.
 */
public enum HealthParameter {

    PULSE("pulse", "Pulso", "bpm", 15,
            a -> a.heartRate() == null ? null : a.heartRate().doubleValue()),
    GLUCOSE("glucose", "Azúcar en sangre", "mg/dL", 30, AstronautSnapshot::bloodSugar),
    TEMPERATURE("temperature", "Temperatura corporal", "°C", 30, AstronautSnapshot::bodyTemperature),
    SLEEP("sleep", "Horas de sueño", "h", 1440, AstronautSnapshot::sleepHours),
    BLOOD_PRESSURE("blood_pressure", "Presión arterial", "mmHg", 30, a -> null),
    OXYGEN("oxygen", "Oxígeno en sangre", "%", 15, AstronautSnapshot::oxygenInBlood),
    BODY_BATTERY("body_battery", "Body Battery", "/ 100", 60,
            a -> a.bodyBattery() == null ? null : a.bodyBattery().doubleValue()),
    HRV("hrv", "VFC (HRV)", "ms", 1440, AstronautSnapshot::hrv),
    RADIATION("radiation", "Radiación espacial", "mSv/día", 60, AstronautSnapshot::spaceRadiation);

    public static final String SYSTOLIC_KEY = "blood_pressure_systolic";
    public static final String DIASTOLIC_KEY = "blood_pressure_diastolic";

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

    /** Valor numérico (la presión arterial se evalúa aparte, ver HealthEvaluator). */
    public Double valueOf(AstronautSnapshot astronaut) {
        return getter.apply(astronaut);
    }
}
