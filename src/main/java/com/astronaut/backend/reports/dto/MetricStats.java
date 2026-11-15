package com.astronaut.backend.reports.dto;

import java.util.Collection;
import java.util.Objects;

/** Mínimo, promedio, máximo y último valor de una métrica (null si no hay datos). */
public record MetricStats(Double min, Double avg, Double max, Double last) {

    public static MetricStats of(Collection<? extends Number> values) {
        var present = values.stream().filter(Objects::nonNull).map(Number::doubleValue).toList();
        if (present.isEmpty()) {
            return new MetricStats(null, null, null, null);
        }
        double min = present.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double max = present.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double avg = present.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return new MetricStats(min, Math.round(avg * 10.0) / 10.0, max, present.get(present.size() - 1));
    }
}
