package com.astronaut.backend.config;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.Mood;
import com.astronaut.backend.repository.AstronautRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * - Si la base está vacía, carga una tripulación de ejemplo.
 * - Si la base viene de una versión anterior (sin nutrición / oxígeno),
 *   completa esos campos con valores por defecto.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final int DEFAULT_NUTRITION = 2500;
    private static final double DEFAULT_OXYGEN = 98.0;

    @Bean
    CommandLineRunner seedData(AstronautRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                List<Astronaut> crew = List.of(
                        new Astronaut("Valentina Rojas", 0.72, 408_000.0, "118/76", 68, 105.0, 36.7, 7.5, 2600, 98.0, Mood.BUENO),
                        new Astronaut("Mateo Fuentes", 0.95, 408_000.0, "124/82", 74, 101.0, 36.9, 6.0, 2900, 97.0, Mood.CANSADO),
                        new Astronaut("Camila Herrera", 0.85, 384_400.0, "115/75", 63, 112.0, 36.5, 8.0, 2400, 99.0, Mood.EXCELENTE),
                        new Astronaut("Diego Navarro", 1.35, 384_400.0, "130/85", 82, 110.0, 37.1, 5.5, 3100, 96.0, Mood.ESTRESADO)
                );
                int[] bodyBattery = {72, 45, 88, 30};
                double[] hrv = {48, 39, 62, 28};
                for (int i = 0; i < crew.size(); i++) {
                    crew.get(i).setBodyBattery(bodyBattery[i]);
                    crew.get(i).setHrv(hrv[i]);
                }
                repository.saveAll(crew);
                log.info("Datos iniciales cargados: 4 astronautas");
                return;
            }

            List<Astronaut> incomplete = repository.findAll().stream()
                    .filter(a -> a.getNutrition() == null || a.getOxygenInBlood() == null || a.getGarminLinked() == null)
                    .toList();
            incomplete.forEach(a -> {
                if (a.getNutrition() == null) {
                    a.setNutrition(DEFAULT_NUTRITION);
                }
                if (a.getOxygenInBlood() == null) {
                    a.setOxygenInBlood(DEFAULT_OXYGEN);
                }
                if (a.getGarminLinked() == null) {
                    a.setGarminLinked(false);
                }
            });
            if (!incomplete.isEmpty()) {
                repository.saveAll(incomplete);
                log.info("Se completaron nutrición/oxígeno en {} registro(s) existentes", incomplete.size());
            }
        };
    }
}
