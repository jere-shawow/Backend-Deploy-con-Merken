package com.astronaut.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Astronaut Monitor (monolito):
 *  - API de astronautas, settings y Garmin           -> MySQL
 *  - notificaciones de salud  (paquete notifications) -> MySQL
 *  - informes diarios         (paquete reports)       -> DynamoDB (respaldo local)
 */
@SpringBootApplication
@EnableScheduling // Garmin, monitor de notificaciones e informes
@ConfigurationPropertiesScan
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
