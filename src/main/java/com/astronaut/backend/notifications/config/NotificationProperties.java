package com.astronaut.backend.notifications.config;

import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.HealthParameter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Configuración "notifications.*" (ver application.yml).
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        Integer pollSeconds,
        Integer timeoutHours,
        Map<String, Long> repeatMinutes,
        Map<String, Long> warningRepeatMinutes) {

    public Duration timeout() {
        return Duration.ofHours(timeoutHours == null ? 24 : timeoutHours);
    }

    /** Cada cuánto se repite la alerta URGENTE (muy bajo / muy alto) mientras siga en ese nivel. */
    public Duration repeatInterval(HealthParameter parameter) {
        Long minutes = repeatMinutes == null ? null : repeatMinutes.get(parameter.key());
        return Duration.ofMinutes(minutes != null ? minutes : parameter.defaultRepeatMinutes());
    }

    /**
     * Cada cuánto se repite la ADVERTENCIA (bajo / alto). Por defecto el doble que la urgente
     * (máximo 1 día), para no saturar con avisos que no son críticos.
     */
    public Duration warningRepeatInterval(HealthParameter parameter) {
        Long minutes = warningRepeatMinutes == null ? null : warningRepeatMinutes.get(parameter.key());
        if (minutes != null) {
            return Duration.ofMinutes(minutes);
        }
        return Duration.ofMinutes(Math.min(repeatInterval(parameter).toMinutes() * 2, 1440));
    }

    /** Intervalo de repetición según el nivel. */
    public Duration repeatInterval(HealthParameter parameter, AlertLevel level) {
        return level.isCritical() ? repeatInterval(parameter) : warningRepeatInterval(parameter);
    }
}
