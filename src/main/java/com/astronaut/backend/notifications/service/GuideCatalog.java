package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.HealthParameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Textos de las notificaciones automáticas: título, cuerpo (de qué se trata)
 * y anexo (guía de qué hacer) para cada parámetro y nivel.
 */
@Component
public class GuideCatalog {

    private static final Locale LOCALE = Locale.of("es", "CL");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_HOUR = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final Map<String, String> GUIDES = Map.of(
            key(HealthParameter.PULSE, AlertLevel.VERY_HIGH), """
                    Guía – Pulso muy alto
                    1. Suspender de inmediato cualquier actividad física o EVA en curso.
                    2. Sentarse o recostarse en un lugar seguro y respirar lento (4 s inhalar, 6 s exhalar).
                    3. Hidratarse si no hay restricción médica.
                    4. Volver a medir el pulso en 5 minutos.
                    5. Si persiste sobre 120 bpm o hay dolor de pecho, mareo o falta de aire: \
                    contactar de inmediato al médico de vuelo / control de misión.""",
            key(HealthParameter.PULSE, AlertLevel.VERY_LOW), """
                    Guía – Pulso muy bajo
                    1. Verificar que la lectura sea correcta (posición del sensor, reloj ajustado).
                    2. Preguntar al astronauta si presenta mareo, debilidad o confusión.
                    3. Mantenerlo en reposo, acostado con las piernas elevadas si hay mareo.
                    4. Volver a medir en 5 minutos.
                    5. Si sigue bajo 40 bpm o hay síntomas: contactar de inmediato al médico de vuelo.""",
            key(HealthParameter.GLUCOSE, AlertLevel.VERY_HIGH), """
                    Guía – Azúcar en sangre muy alta
                    1. Confirmar con una segunda medición.
                    2. Beber agua y evitar alimentos con azúcar o carbohidratos rápidos.
                    3. Revisar si hay medicación indicada por el equipo médico y seguir ese protocolo.
                    4. Vigilar sed intensa, náuseas, visión borrosa o confusión.
                    5. Informar al médico de vuelo; si hay síntomas, de forma inmediata.""",
            key(HealthParameter.GLUCOSE, AlertLevel.VERY_LOW), """
                    Guía – Azúcar en sangre muy baja
                    1. Dar 15 g de carbohidratos de acción rápida (jugo, tabletas de glucosa).
                    2. Esperar 15 minutos y volver a medir.
                    3. Si sigue bajo 70 mg/dL, repetir los 15 g de carbohidratos.
                    4. Al normalizarse, comer una colación con proteína.
                    5. Si hay confusión o pérdida de conciencia: emergencia, contactar al médico de vuelo.""",
            key(HealthParameter.TEMPERATURE, AlertLevel.VERY_HIGH), """
                    Guía – Temperatura corporal muy alta
                    1. Detener toda actividad y trasladar a un área fresca y ventilada.
                    2. Retirar ropa o equipo que retenga calor; aplicar paños húmedos.
                    3. Hidratar con líquidos frescos si está consciente.
                    4. Medir la temperatura cada 15 minutos.
                    5. Desde 40 °C es una emergencia: contactar de inmediato al médico de vuelo.""",
            key(HealthParameter.TEMPERATURE, AlertLevel.VERY_LOW), """
                    Guía – Temperatura corporal muy baja
                    1. Llevar a un área temperada y retirar ropa húmeda.
                    2. Cubrir con mantas o ropa seca, priorizando tronco y cabeza.
                    3. Dar bebidas tibias si está consciente (sin alcohol ni cafeína).
                    4. Evitar frotar las extremidades; medir la temperatura cada 15 minutos.
                    5. Bajo 35 °C o con confusión: contactar de inmediato al médico de vuelo.""",
            key(HealthParameter.SLEEP, AlertLevel.VERY_HIGH), """
                    Guía – Sueño excesivo
                    1. Confirmar con el astronauta cómo se siente (somnolencia, fiebre, malestar).
                    2. Revisar si hubo medicación, esfuerzo extremo o enfermedad reciente.
                    3. Mantener horarios regulares de luz y actividad durante el día.
                    4. Si se repite varios días o hay otros síntomas, informar al médico de vuelo.""",
            key(HealthParameter.SLEEP, AlertLevel.VERY_LOW), """
                    Guía – Falta de sueño
                    1. Evitar asignar tareas críticas o EVA hasta recuperar descanso.
                    2. Programar un período de descanso o siesta de 20–30 minutos.
                    3. Revisar ruido, luz y temperatura del área de descanso.
                    4. Evitar cafeína y pantallas antes del próximo período de sueño.
                    5. Si se repite, informar al médico de vuelo para ajustar el plan de trabajo.""");

    private final HealthRangeClassifier ranges;
    private final ZoneId zone;

    public GuideCatalog(HealthRangeClassifier ranges,
                        @Value("${notifications.time-zone:America/Santiago}") String timeZone) {
        this.ranges = ranges;
        this.zone = ZoneId.of(timeZone);
    }

    public String title(HealthParameter parameter, AlertLevel level, String astronautName) {
        return "ALERTA: " + parameter.label() + " " + level.label() + " – " + nameOr(astronautName);
    }

    /** Cuerpo de la notificación automática; incluye la hora en que se generó. */
    public String body(HealthParameter parameter, AlertLevel level, double value,
                       String astronautName, Duration repeatEvery, Instant generatedAt) {
        return ("[%s] %s %s: %s %s para %s (rango normal %s %s). "
                + "Detectado a las %s (%s). Se volverá a avisar cada %s mientras siga en este nivel.")
                .formatted(DATE_HOUR.format(generatedAt.atZone(zone)),
                        parameter.label(), level.label(), number(value), parameter.unit(),
                        nameOr(astronautName), ranges.normalRange(parameter.key()), parameter.unit(),
                        HOUR.format(generatedAt.atZone(zone)), zone.getId(), human(repeatEvery));
    }

    public String annex(HealthParameter parameter, AlertLevel level) {
        return GUIDES.get(key(parameter, level));
    }

    private static String key(HealthParameter p, AlertLevel l) {
        return p.name() + "_" + l.name();
    }

    private static String nameOr(String name) {
        return name == null || name.isBlank() ? "astronauta sin nombre" : name;
    }

    private static String number(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(LOCALE);
        nf.setMaximumFractionDigits(1);
        return nf.format(value);
    }

    static String human(Duration d) {
        long minutes = d.toMinutes();
        if (minutes % 1440 == 0) {
            long days = minutes / 1440;
            return days == 1 ? "1 día" : days + " días";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        return minutes + " minutos";
    }
}
