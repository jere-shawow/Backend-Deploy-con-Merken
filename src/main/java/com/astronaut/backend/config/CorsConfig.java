package com.astronaut.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS: permite que un frontend web en otro dominio (p. ej. el front en la nube)
 * llame a la API desde el navegador.
 *
 * CORS_ALLOWED_ORIGINS = lista separada por comas, ej.
 *   https://mi-front.amplifyapp.com,http://localhost:5173
 * Por defecto "*" (cualquier origen). En producción conviene poner solo la URL del front.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String allowedOrigins) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        // vacío = cualquier origen (igual que "*")
        this.allowedOrigins = origins.length == 0 ? new String[]{"*"} : origins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location")
                .maxAge(3600);
    }
}
