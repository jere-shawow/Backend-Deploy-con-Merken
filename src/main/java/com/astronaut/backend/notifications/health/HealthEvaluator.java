package com.astronaut.backend.notifications.health;

import com.astronaut.backend.notifications.client.AstronautSnapshot;
import com.astronaut.backend.notifications.model.HealthParameter;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evalúa un parámetro de un astronauta con health-ranges.json:
 * nivel (muy bajo ... muy alto) y gravedad (ok / warning / critical).
 * La presión arterial combina sistólica y diastólica y se queda con la peor.
 */
@Component
public class HealthEvaluator {

    private static final Pattern BP = Pattern.compile("^\\s*(\\d{2,3})\\s*/\\s*(\\d{2,3})\\s*$");

    private final HealthRangeClassifier ranges;

    public HealthEvaluator(HealthRangeClassifier ranges) {
        this.ranges = ranges;
    }

    /**
     * Resultado de la evaluación.
     *
     * @param rangeKey     clave del rango que decidió (para la presión: sistólica o diastólica)
     * @param value        valor numérico evaluado (para la presión: el componente que decidió)
     * @param displayValue texto para mostrar, ej. "131" o "150/95"
     */
    public record Evaluation(HealthParameter parameter, String rangeKey, HealthStatus status,
                             RangeSeverity severity, double value, String displayValue) {

        public boolean needsAlert() {
            return severity != RangeSeverity.OK;
        }
    }

    public Optional<Evaluation> evaluate(HealthParameter parameter, AstronautSnapshot astronaut) {
        if (parameter == HealthParameter.BLOOD_PRESSURE) {
            return bloodPressure(astronaut.bloodPressure());
        }
        Double value = parameter.valueOf(astronaut);
        if (value == null) {
            return Optional.empty();
        }
        return evaluate(parameter, parameter.key(), value, number(value));
    }

    /** Presión "sistólica/diastólica": se evalúan ambas y vale la de mayor gravedad. */
    public Optional<Evaluation> bloodPressure(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = BP.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        String display = m.group(1) + "/" + m.group(2);
        Optional<Evaluation> sys = evaluate(HealthParameter.BLOOD_PRESSURE, HealthParameter.SYSTOLIC_KEY,
                Double.parseDouble(m.group(1)), display);
        Optional<Evaluation> dia = evaluate(HealthParameter.BLOOD_PRESSURE, HealthParameter.DIASTOLIC_KEY,
                Double.parseDouble(m.group(2)), display);
        if (sys.isEmpty()) {
            return dia;
        }
        if (dia.isEmpty()) {
            return sys;
        }
        return dia.get().severity().worseThan(sys.get().severity()) ? dia : sys;
    }

    private Optional<Evaluation> evaluate(HealthParameter parameter, String rangeKey, double value, String display) {
        return ranges.classify(rangeKey, value)
                .map(status -> new Evaluation(parameter, rangeKey, status,
                        ranges.severity(rangeKey, status), value, display));
    }

    private static String number(double v) {
        return v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
    }
}
