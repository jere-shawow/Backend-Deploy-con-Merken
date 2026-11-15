package com.astronaut.backend.service;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.notifications.health.HealthEvaluator;
import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.health.HealthStatus;
import com.astronaut.backend.notifications.health.RangeSeverity;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Estado de salud de cada astronauta según health-ranges.json, ya calculado,
 * para que cualquier frontend lo muestre sin repetir la lógica de rangos.
 */
@Service
public class AstronautHealthService {

    private static final String BLOOD_PRESSURE = "blood_pressure";

    private final AstronautService astronauts;
    private final HealthRangeClassifier ranges;
    private final HealthEvaluator evaluator;

    public AstronautHealthService(AstronautService astronauts, HealthRangeClassifier ranges,
                                  HealthEvaluator evaluator) {
        this.astronauts = astronauts;
        this.ranges = ranges;
        this.evaluator = evaluator;
    }

    /** Estado de un parámetro. status/severity son null si no hay dato. */
    public record ParameterStatus(
            String parameter,
            String field,
            String name,
            String value,
            String unit,
            String status,
            String severity,
            String description,
            @com.fasterxml.jackson.annotation.JsonProperty("notify") boolean notificationEnabled) {
    }

    /** overall = la peor gravedad entre todos los parámetros (OK, WARNING o CRITICAL). */
    public record AstronautHealth(Long astronautId, String astronautName, String overall,
                                  List<ParameterStatus> parameters) {
    }

    public List<AstronautHealth> all() {
        return astronauts.findAll().stream().map(this::of).toList();
    }

    public AstronautHealth byId(Long id) {
        return of(astronauts.findById(id));
    }

    public AstronautHealth of(Astronaut a) {
        List<ParameterStatus> list = new ArrayList<>();
        RangeSeverity worst = RangeSeverity.OK;
        for (String key : ranges.keys()) {
            if (HealthParameter.DIASTOLIC_KEY.equals(key)) {
                continue; // se informa junto con la sistólica como "blood_pressure"
            }
            HealthRangeClassifier.ParameterInfo info = ranges.info(key).orElseThrow();
            ParameterStatus st = HealthParameter.SYSTOLIC_KEY.equals(key) ? bloodPressure(a, info) : simple(a, key, info);
            list.add(st);
            if (st.severity() != null) {
                RangeSeverity sev = RangeSeverity.valueOf(st.severity());
                if (sev.worseThan(worst)) {
                    worst = sev;
                }
            }
        }
        return new AstronautHealth(a.getId(), a.getAstronautName(), worst.name(), list);
    }

    private ParameterStatus simple(Astronaut a, String key, HealthRangeClassifier.ParameterInfo info) {
        Double value = value(a, info.field());
        if (value == null) {
            return new ParameterStatus(key, info.field(), info.name(), null, info.unit(), null, null, null, info.notificationEnabled());
        }
        Optional<HealthStatus> status = ranges.classify(key, value);
        return new ParameterStatus(key, info.field(), info.name(), number(value), info.unit(),
                status.map(Enum::name).orElse(null),
                status.map(s -> ranges.severity(key, s).name()).orElse(null),
                status.flatMap(s -> ranges.description(key, s)).orElse(null),
                info.notificationEnabled());
    }

    private ParameterStatus bloodPressure(Astronaut a, HealthRangeClassifier.ParameterInfo info) {
        return evaluator.bloodPressure(a.getBloodPressure())
                .map(e -> new ParameterStatus(BLOOD_PRESSURE, "bloodPressure", "Presión arterial", e.displayValue(),
                        info.unit(), e.status().name(), e.severity().name(),
                        ranges.description(e.rangeKey(), e.status()).orElse(null), info.notificationEnabled()))
                .orElseGet(() -> new ParameterStatus(BLOOD_PRESSURE, "bloodPressure", "Presión arterial",
                        a.getBloodPressure(), info.unit(), null, null, null, info.notificationEnabled()));
    }

    /** Valor del campo del astronauta indicado en "field" de health-ranges.json. */
    static Double value(Astronaut a, String field) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case "spaceRadiation" -> a.getSpaceRadiation();
            case "distanceFromEarth" -> a.getDistanceFromEarth();
            case "heartRate" -> a.getHeartRate() == null ? null : a.getHeartRate().doubleValue();
            case "bloodSugar" -> a.getBloodSugar();
            case "bodyTemperature" -> a.getBodyTemperature();
            case "sleepHours" -> a.getSleepHours();
            case "oxygenInBlood" -> a.getOxygenInBlood();
            case "bodyBattery" -> a.getBodyBattery() == null ? null : a.getBodyBattery().doubleValue();
            case "hrv" -> a.getHrv();
            case "nutrition" -> a.getNutrition() == null ? null : a.getNutrition().doubleValue();
            default -> null;
        };
    }

    private static String number(double v) {
        return v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
    }
}
