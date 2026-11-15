package com.astronaut.backend.notifications.config;

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
        Map<String, Long> repeatMinutes) {

    public Duration timeout() {
        return Duration.ofHours(timeoutHours == null ? 24 : timeoutHours);
    }

    /** Cada cuánto se repite la alerta de un parámetro mientras siga crítico. */
    public Duration repeatInterval(HealthParameter parameter) {
        Long minutes = repeatMinutes == null ? null : repeatMinutes.get(parameter.key());
        return Duration.ofMinutes(minutes != null ? minutes : parameter.defaultRepeatMinutes());
    }
}
