package com.astronaut.backend.notifications.health;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Clasifica valores según health-ranges.json (los mismos rangos que usa el frontend
 * para los colores verde / naranjo / rojo). Cada rango tiene su gravedad
 * (ok / warning / critical): p. ej. Body Battery "muy alto" es bueno (ok).
 */
@Component
public class HealthRangeClassifier {

    private static final String RESOURCE = "/health-ranges.json";

    private final Map<String, ParameterDef> parameters = new LinkedHashMap<>();
    private final JsonNode document;

    public HealthRangeClassifier(ObjectMapper objectMapper) {
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream(RESOURCE), "No se encontró " + RESOURCE)) {
            document = objectMapper.readTree(in);
            Root root = objectMapper.treeToValue(document, Root.class);
            root.healthParameters().forEach(p -> parameters.put(p.parameter(), p));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RESOURCE, e);
        }
    }

    public Optional<HealthStatus> classify(String parameter, double value) {
        ParameterDef def = parameters.get(parameter);
        if (def == null || def.ranges() == null) {
            return Optional.empty();
        }
        for (HealthStatus status : HealthStatus.values()) {
            RangeDef range = def.ranges().get(status.jsonKey());
            if (range != null && range.contains(value)) {
                return Optional.of(status);
            }
        }
        // Hueco entre rangos: bajo si está bajo lo normal, alto si está sobre lo normal
        RangeDef normal = def.ranges().get(HealthStatus.NORMAL.jsonKey());
        if (normal != null) {
            if (normal.min() != null && value < normal.min()) {
                return Optional.of(HealthStatus.LOW);
            }
            if (normal.max() != null && value > normal.max()) {
                return Optional.of(HealthStatus.HIGH);
            }
        }
        return Optional.empty();
    }

    /** Gravedad del nivel: ok (verde), warning (naranjo) o critical (rojo). */
    public RangeSeverity severity(String parameter, HealthStatus status) {
        if (status == HealthStatus.NORMAL) {
            return RangeSeverity.OK;
        }
        ParameterDef def = parameters.get(parameter);
        RangeDef range = def == null || def.ranges() == null ? null : def.ranges().get(status.jsonKey());
        if (range == null || range.severity() == null) {
            // compatibilidad: sin "severity" -> muy bajo/alto rojo, bajo/alto naranjo
            return status == HealthStatus.VERY_LOW || status == HealthStatus.VERY_HIGH
                    ? RangeSeverity.CRITICAL : RangeSeverity.WARNING;
        }
        return RangeSeverity.parse(range.severity());
    }

    /** Descripción del rango (texto de health-ranges.json), ej. "101 – 115 lpm". */
    public Optional<String> description(String parameter, HealthStatus status) {
        ParameterDef def = parameters.get(parameter);
        RangeDef range = def == null || def.ranges() == null ? null : def.ranges().get(status.jsonKey());
        return Optional.ofNullable(range == null ? null : range.description());
    }

    /** Si el límite del rango incluye el valor exacto (para redactar "supera" vs "llega a ... o más"). */
    public boolean inclusive(String parameter, HealthStatus status, boolean min) {
        ParameterDef def = parameters.get(parameter);
        RangeDef range = def == null || def.ranges() == null ? null : def.ranges().get(status.jsonKey());
        if (range == null) {
            return true;
        }
        Boolean flag = min ? range.minInclusive() : range.maxInclusive();
        return !Boolean.FALSE.equals(flag);
    }

    /** El archivo completo, tal cual, para exponerlo en GET /api/health-ranges. */
    public JsonNode document() {
        return document;
    }

    /** Claves de los parámetros en el orden del archivo. */
    public java.util.Set<String> keys() {
        return parameters.keySet();
    }

    public Optional<ParameterInfo> info(String parameter) {
        ParameterDef def = parameters.get(parameter);
        return Optional.ofNullable(def == null ? null
                : new ParameterInfo(def.parameter(), def.name(), def.field(), def.unit(),
                        !Boolean.FALSE.equals(def.notificationEnabled())));
    }

    /** Datos básicos de un parámetro. */
    public record ParameterInfo(String parameter, String name, String field, String unit, @com.fasterxml.jackson.annotation.JsonProperty("notify") boolean notificationEnabled) {
    }

    /**
     * Límite de un rango: min=true devuelve el mínimo, si no el máximo.
     * Ej. bound("pulse", NORMAL, false) = 100; bound("pulse", VERY_HIGH, true) = 120.
     */
    public Optional<Double> bound(String parameter, HealthStatus status, boolean min) {
        ParameterDef def = parameters.get(parameter);
        RangeDef range = def == null || def.ranges() == null ? null : def.ranges().get(status.jsonKey());
        if (range == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(min ? range.min() : range.max());
    }

    /** Texto del rango normal, ej. "60 – 100". */
    public String normalRange(String parameter) {
        ParameterDef def = parameters.get(parameter);
        RangeDef normal = def == null || def.ranges() == null ? null : def.ranges().get("normal");
        if (normal == null) {
            return "-";
        }
        return fmt(normal.min()) + " – " + fmt(normal.max());
    }

    private static String fmt(Double v) {
        if (v == null) {
            return "?";
        }
        return v % 1 == 0 ? String.valueOf(v.longValue()) : String.valueOf(v).replace('.', ',');
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Root(@JsonProperty("health_parameters") List<ParameterDef> healthParameters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParameterDef(String parameter, String name, String field, String unit, @JsonProperty("notify") Boolean notificationEnabled,
                        Map<String, RangeDef> ranges) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RangeDef(Double min, Double max,
                    @JsonProperty("min_inclusive") Boolean minInclusive,
                    @JsonProperty("max_inclusive") Boolean maxInclusive,
                    String severity,
                    String description) {

        boolean contains(double v) {
            boolean minOk = min == null || (Boolean.FALSE.equals(minInclusive) ? v > min : v >= min);
            boolean maxOk = max == null || (Boolean.FALSE.equals(maxInclusive) ? v < max : v <= max);
            return minOk && maxOk;
        }
    }
}
