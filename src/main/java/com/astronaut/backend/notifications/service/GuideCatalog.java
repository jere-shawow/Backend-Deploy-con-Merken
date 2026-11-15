package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.health.HealthStatus;
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
import java.util.Optional;

/**
 * Textos de las notificaciones automáticas: título, cuerpo (de qué se trata)
 * y anexo (guía de qué hacer) para cada parámetro y nivel:
 *  - ADVERTENCIA para bajo / alto
 *  - URGENTE para muy bajo / muy alto
 */
@Component
public class GuideCatalog {

    private static final Locale LOCALE = Locale.of("es", "CL");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_HOUR = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final Map<String, String> GUIDES = Map.ofEntries(
            Map.entry(key(HealthParameter.PULSE, AlertLevel.VERY_HIGH), """
                    Guía – Pulso muy alto
                    1. Suspender de inmediato cualquier actividad física o EVA en curso.
                    2. Sentarse o recostarse en un lugar seguro y respirar lento (4 s inhalar, 6 s exhalar).
                    3. Hidratarse si no hay restricción médica.
                    4. Volver a medir el pulso en 5 minutos.
                    5. Si persiste sobre 120 bpm o hay dolor de pecho, mareo o falta de aire: \
                    contactar de inmediato al médico de vuelo / control de misión."""),
            Map.entry(key(HealthParameter.PULSE, AlertLevel.HIGH), """
                    Guía – Pulso alto
                    1. Reducir el ritmo de la actividad y descansar 10 minutos sentado.
                    2. Respirar lento y profundo (4 s inhalar, 6 s exhalar).
                    3. Hidratarse y revisar la temperatura del ambiente o del traje.
                    4. Volver a medir en 10 minutos.
                    5. Si sigue subiendo o aparece mareo o dolor de pecho, tratarlo como urgente."""),
            Map.entry(key(HealthParameter.PULSE, AlertLevel.LOW), """
                    Guía – Pulso bajo
                    1. Verificar la lectura (posición del sensor, reloj bien ajustado).
                    2. Preguntar si hay mareo, cansancio inusual o debilidad.
                    3. Si está en reposo y sin síntomas, puede ser normal: seguir observando.
                    4. Volver a medir en 10 minutos.
                    5. Si baja más o aparecen síntomas, tratarlo como urgente."""),
            Map.entry(key(HealthParameter.PULSE, AlertLevel.VERY_LOW), """
                    Guía – Pulso muy bajo
                    1. Verificar que la lectura sea correcta (posición del sensor, reloj ajustado).
                    2. Preguntar al astronauta si presenta mareo, debilidad o confusión.
                    3. Mantenerlo en reposo, acostado con las piernas elevadas si hay mareo.
                    4. Volver a medir en 5 minutos.
                    5. Si sigue bajo 40 bpm o hay síntomas: contactar de inmediato al médico de vuelo."""),
            Map.entry(key(HealthParameter.GLUCOSE, AlertLevel.VERY_HIGH), """
                    Guía – Azúcar en sangre muy alta
                    1. Confirmar con una segunda medición.
                    2. Beber agua y evitar alimentos con azúcar o carbohidratos rápidos.
                    3. Revisar si hay medicación indicada por el equipo médico y seguir ese protocolo.
                    4. Vigilar sed intensa, náuseas, visión borrosa o confusión.
                    5. Informar al médico de vuelo; si hay síntomas, de forma inmediata."""),
            Map.entry(key(HealthParameter.GLUCOSE, AlertLevel.HIGH), """
                    Guía – Azúcar en sangre alta
                    1. Evitar alimentos y bebidas azucaradas en las próximas horas.
                    2. Beber agua.
                    3. Realizar actividad suave si no hay restricción médica.
                    4. Volver a medir en 30 minutos.
                    5. Si sigue subiendo o hay sed intensa o visión borrosa, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.GLUCOSE, AlertLevel.LOW), """
                    Guía – Azúcar en sangre baja
                    1. Comer una colación con carbohidratos (fruta, galletas, jugo).
                    2. Suspender tareas que exijan esfuerzo físico hasta normalizar.
                    3. Volver a medir en 15 minutos.
                    4. Vigilar temblor, sudor frío o confusión.
                    5. Si sigue bajando, aplicar la guía de azúcar muy baja."""),
            Map.entry(key(HealthParameter.GLUCOSE, AlertLevel.VERY_LOW), """
                    Guía – Azúcar en sangre muy baja
                    1. Dar 15 g de carbohidratos de acción rápida (jugo, tabletas de glucosa).
                    2. Esperar 15 minutos y volver a medir.
                    3. Si sigue bajo 70 mg/dL, repetir los 15 g de carbohidratos.
                    4. Al normalizarse, comer una colación con proteína.
                    5. Si hay confusión o pérdida de conciencia: emergencia, contactar al médico de vuelo."""),
            Map.entry(key(HealthParameter.TEMPERATURE, AlertLevel.VERY_HIGH), """
                    Guía – Temperatura corporal muy alta
                    1. Detener toda actividad y trasladar a un área fresca y ventilada.
                    2. Retirar ropa o equipo que retenga calor; aplicar paños húmedos.
                    3. Hidratar con líquidos frescos si está consciente.
                    4. Medir la temperatura cada 15 minutos.
                    5. Desde 40 °C es una emergencia: contactar de inmediato al médico de vuelo."""),
            Map.entry(key(HealthParameter.TEMPERATURE, AlertLevel.HIGH), """
                    Guía – Temperatura corporal alta
                    1. Disminuir la actividad y descansar en un área fresca.
                    2. Hidratarse con líquidos frescos.
                    3. Aflojar ropa o equipo que retenga calor.
                    4. Volver a medir en 30 minutos.
                    5. Si sigue subiendo o hay escalofríos o malestar, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.TEMPERATURE, AlertLevel.LOW), """
                    Guía – Temperatura corporal baja
                    1. Abrigar con ropa seca y cubrir cabeza y manos.
                    2. Ofrecer una bebida tibia (sin alcohol ni cafeína).
                    3. Revisar la temperatura del módulo o del traje.
                    4. Volver a medir en 30 minutos.
                    5. Si sigue bajando o hay temblor intenso, aplicar la guía de temperatura muy baja."""),
            Map.entry(key(HealthParameter.TEMPERATURE, AlertLevel.VERY_LOW), """
                    Guía – Temperatura corporal muy baja
                    1. Llevar a un área temperada y retirar ropa húmeda.
                    2. Cubrir con mantas o ropa seca, priorizando tronco y cabeza.
                    3. Dar bebidas tibias si está consciente (sin alcohol ni cafeína).
                    4. Evitar frotar las extremidades; medir la temperatura cada 15 minutos.
                    5. Bajo 35 °C o con confusión: contactar de inmediato al médico de vuelo."""),
            Map.entry(key(HealthParameter.SLEEP, AlertLevel.VERY_HIGH), """
                    Guía – Sueño excesivo
                    1. Confirmar con el astronauta cómo se siente (somnolencia, fiebre, malestar).
                    2. Revisar si hubo medicación, esfuerzo extremo o enfermedad reciente.
                    3. Mantener horarios regulares de luz y actividad durante el día.
                    4. Si se repite varios días o hay otros síntomas, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.SLEEP, AlertLevel.HIGH), """
                    Guía – Horas de sueño altas
                    1. Consultar cómo se siente (somnolencia, desánimo, malestar).
                    2. Mantener horarios regulares de luz y actividad.
                    3. Evitar siestas largas durante el día.
                    4. Si se repite varios días, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.SLEEP, AlertLevel.LOW), """
                    Guía – Pocas horas de sueño
                    1. Evitar tareas de alta exigencia o riesgo en el próximo turno si es posible.
                    2. Planificar un descanso corto (20–30 minutos).
                    3. Revisar ruido, luz y temperatura del área de descanso.
                    4. Evitar cafeína y pantallas antes del próximo período de sueño.
                    5. Si se repite, informar al médico de vuelo para ajustar el plan de trabajo."""),
            Map.entry(key(HealthParameter.SLEEP, AlertLevel.VERY_LOW), """
                    Guía – Falta de sueño
                    1. Evitar asignar tareas críticas o EVA hasta recuperar descanso.
                    2. Programar un período de descanso o siesta de 20–30 minutos.
                    3. Revisar ruido, luz y temperatura del área de descanso.
                    4. Evitar cafeína y pantallas antes del próximo período de sueño.
                    5. Si se repite, informar al médico de vuelo para ajustar el plan de trabajo."""));

    private final HealthRangeClassifier ranges;
    private final ZoneId zone;

    public GuideCatalog(HealthRangeClassifier ranges,
                        @Value("${notifications.time-zone:America/Santiago}") String timeZone) {
        this.ranges = ranges;
        this.zone = ZoneId.of(timeZone);
    }

    /** Ej. "URGENTE: Pulso muy alto – Valentina" o "ADVERTENCIA: Azúcar en sangre alta – Mateo". */
    public String title(HealthParameter parameter, AlertLevel level, String astronautName) {
        return (level.isCritical() ? "URGENTE: " : "ADVERTENCIA: ")
                + parameter.label() + " " + adjective(parameter, level) + " – " + nameOr(astronautName);
    }

    public String body(HealthParameter parameter, AlertLevel level, double value,
                       String astronautName, Duration repeatEvery, Instant generatedAt) {
        return body(parameter, level, null, value, astronautName, repeatEvery, generatedAt);
    }

    /**
     * Cuerpo de la notificación automática. Explica:
     * fecha y hora, tipo (advertencia / urgente), valor, cuánto se aleja del rango normal,
     * si empeoró o mejoró respecto del aviso anterior, qué hacer y cada cuánto se repite.
     */
    public String body(HealthParameter parameter, AlertLevel level, AlertLevel previous, double value,
                       String astronautName, Duration repeatEvery, Instant generatedAt) {
        String unit = parameter.unit();
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(DATE_HOUR.format(generatedAt.atZone(zone))).append("] ")
          .append(level.isCritical() ? "URGENTE – " : "ADVERTENCIA – ")
          .append(parameter.label()).append(' ').append(adjective(parameter, level)).append(": ")
          .append(nameOr(astronautName)).append(" registra ").append(number(value)).append(' ').append(unit);

        Optional<Double> limit = level.isBelowNormal()
                ? ranges.bound(parameter.key(), HealthStatus.NORMAL, true)
                : ranges.bound(parameter.key(), HealthStatus.NORMAL, false);
        if (limit.isPresent()) {
            double diff = Math.abs(value - limit.get());
            sb.append(", ").append(number(diff)).append(' ').append(unit)
              .append(level.isBelowNormal() ? " bajo el mínimo normal" : " sobre el máximo normal");
        }
        sb.append(" (rango normal ").append(ranges.normalRange(parameter.key())).append(' ').append(unit).append("). ");

        sb.append(trend(parameter, level, previous));

        if (level.isCritical()) {
            sb.append("Es un nivel crítico y requiere atención inmediata: siga la guía adjunta y, "
                    + "si no mejora, contacte al médico de vuelo. ");
        } else {
            sb.append("Aún no es crítico, pero conviene vigilarlo y seguir la guía adjunta. ");
            criticalThreshold(parameter, level).ifPresent(t -> sb.append("Si llega a ")
                    .append(number(t)).append(' ').append(unit)
                    .append(" o ").append(level.isBelowNormal() ? "menos" : "más")
                    .append(" se enviará una alerta urgente. "));
        }

        sb.append("Detectado a las ").append(HOUR.format(generatedAt.atZone(zone)))
          .append(" (").append(zone.getId()).append("). Se volverá a avisar cada ").append(human(repeatEvery))
          .append(" mientras siga en este nivel.");
        return sb.toString();
    }

    public String annex(HealthParameter parameter, AlertLevel level) {
        return GUIDES.get(key(parameter, level));
    }

    /** "muy alto", "alta", "muy bajas"... según el género / número del parámetro. */
    static String adjective(HealthParameter parameter, AlertLevel level) {
        String base = level.label();
        return switch (parameter) {
            case GLUCOSE, TEMPERATURE -> base.substring(0, base.length() - 1) + "a";
            case SLEEP -> base.substring(0, base.length() - 1) + "as";
            default -> base;
        };
    }

    private String trend(HealthParameter parameter, AlertLevel level, AlertLevel previous) {
        if (previous == null) {
            return "";
        }
        if (previous == level) {
            return "El valor se mantiene " + adjective(parameter, level) + " desde el aviso anterior. ";
        }
        boolean worse = level.isCritical() && !previous.isCritical();
        boolean better = !level.isCritical() && previous.isCritical();
        if (worse) {
            return "Empeoró: en el aviso anterior estaba " + adjective(parameter, previous) + ". ";
        }
        if (better) {
            return "Mejoró respecto del aviso anterior (" + adjective(parameter, previous)
                    + "), pero sigue fuera del rango normal. ";
        }
        return "Cambió desde " + adjective(parameter, previous) + " en el aviso anterior. ";
    }

    /** Valor desde el cual una advertencia pasa a urgente (ej. pulso 120, azúcar 54). */
    private Optional<Double> criticalThreshold(HealthParameter parameter, AlertLevel level) {
        return level.isBelowNormal()
                ? ranges.bound(parameter.key(), HealthStatus.VERY_LOW, false)
                : ranges.bound(parameter.key(), HealthStatus.VERY_HIGH, true);
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
