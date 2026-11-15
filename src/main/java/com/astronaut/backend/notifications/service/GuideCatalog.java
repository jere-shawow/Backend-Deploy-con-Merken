package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.health.HealthRangeClassifier;
import com.astronaut.backend.notifications.health.HealthStatus;
import com.astronaut.backend.notifications.health.RangeSeverity;
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
                    5. Si se repite, informar al médico de vuelo para ajustar el plan de trabajo."""),
            Map.entry(key(HealthParameter.BLOOD_PRESSURE, AlertLevel.VERY_HIGH), """
                    Guía – Presión arterial muy alta (hipertensión)
                    1. Suspender esfuerzo físico y mantener al astronauta sentado y en calma.
                    2. Repetir la medición en 5 minutos con el brazo a la altura del corazón.
                    3. Vigilar dolor de cabeza intenso, visión borrosa, dolor de pecho o confusión.
                    4. Informar de inmediato al médico de vuelo con ambas mediciones.
                    5. Si aparecen síntomas, activar el protocolo de emergencia médica."""),
            Map.entry(key(HealthParameter.BLOOD_PRESSURE, AlertLevel.HIGH), """
                    Guía – Presión arterial alta (prehipertensión)
                    1. Reducir la actividad y descansar 10 minutos.
                    2. Hidratarse y evitar cafeína y sal en las próximas horas.
                    3. Repetir la medición en 30 minutos.
                    4. Si se mantiene alta durante el día, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.BLOOD_PRESSURE, AlertLevel.LOW), """
                    Guía – Presión arterial baja
                    1. Sentar o recostar al astronauta y elevar las piernas.
                    2. Hidratarse con líquidos (idealmente con electrolitos).
                    3. Levantarse lentamente para evitar mareos.
                    4. Repetir la medición en 30 minutos."""),
            Map.entry(key(HealthParameter.BLOOD_PRESSURE, AlertLevel.VERY_LOW), """
                    Guía – Presión arterial muy baja (hipotensión severa)
                    1. Recostar al astronauta con las piernas elevadas y no dejarlo solo.
                    2. Vigilar mareo, desmayo, piel fría o confusión.
                    3. Administrar líquidos si está consciente y puede tragar.
                    4. Informar de inmediato al médico de vuelo.
                    5. Si pierde la conciencia, activar el protocolo de emergencia médica."""),
            Map.entry(key(HealthParameter.OXYGEN, AlertLevel.LOW), """
                    Guía – Oxígeno en sangre bajo
                    1. Verificar el sensor (posición, dedo tibio, sin movimiento) y repetir la medición.
                    2. Revisar la atmósfera del módulo o el suministro del traje.
                    3. Respirar profundo y reducir el esfuerzo físico.
                    4. Volver a medir en 10 minutos.
                    5. Si sigue bajando, aplicar la guía de oxígeno muy bajo."""),
            Map.entry(key(HealthParameter.OXYGEN, AlertLevel.VERY_LOW), """
                    Guía – Oxígeno en sangre muy bajo (hipoxia)
                    1. Administrar oxígeno suplementario de inmediato.
                    2. Revisar presión y composición de la atmósfera del módulo o del traje.
                    3. Suspender toda actividad y vigilar confusión, labios azulados o falta de aire.
                    4. Informar de inmediato al médico de vuelo.
                    5. Si no mejora, activar el protocolo de emergencia médica."""),
            Map.entry(key(HealthParameter.BODY_BATTERY, AlertLevel.LOW), """
                    Guía – Body Battery bajo
                    1. Reducir la carga de trabajo en lo que queda del turno.
                    2. Programar un descanso corto (20–30 minutos).
                    3. Hidratarse y comer una colación equilibrada.
                    4. Priorizar un período de sueño completo en el próximo ciclo."""),
            Map.entry(key(HealthParameter.BODY_BATTERY, AlertLevel.VERY_LOW), """
                    Guía – Body Battery muy bajo (agotamiento)
                    1. Suspender tareas críticas o de riesgo (EVA, maniobras) hasta recuperarse.
                    2. Indicar descanso inmediato.
                    3. Revisar sueño, estrés y alimentación de las últimas 24 horas.
                    4. Informar al médico de vuelo para ajustar el plan de trabajo."""),
            Map.entry(key(HealthParameter.HRV, AlertLevel.LOW), """
                    Guía – VFC (HRV) baja
                    1. Reducir la intensidad del ejercicio en el día.
                    2. Priorizar descanso y técnicas de relajación (respiración lenta).
                    3. Revisar calidad del sueño y nivel de estrés.
                    4. Si se mantiene baja varios días, informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.HRV, AlertLevel.VERY_LOW), """
                    Guía – VFC (HRV) muy baja
                    1. Suspender entrenamiento intenso y tareas físicas exigentes.
                    2. Indicar descanso y vigilar síntomas de enfermedad (fiebre, malestar).
                    3. Revisar sueño, hidratación y estrés de las últimas 24 horas.
                    4. Informar al médico de vuelo."""),
            Map.entry(key(HealthParameter.RADIATION, AlertLevel.HIGH), """
                    Guía – Radiación espacial alta
                    1. Verificar la lectura del dosímetro y el pronóstico de clima espacial.
                    2. Reducir el tiempo en zonas menos protegidas del módulo.
                    3. Postergar actividades extravehiculares (EVA) no esenciales.
                    4. Registrar la dosis acumulada e informar al control de misión."""),
            Map.entry(key(HealthParameter.RADIATION, AlertLevel.VERY_HIGH), """
                    Guía – Radiación espacial muy alta
                    1. Trasladarse de inmediato al refugio o zona más blindada del módulo.
                    2. Suspender toda actividad extravehicular (EVA).
                    3. Informar al control de misión y al médico de vuelo.
                    4. Registrar la dosis y permanecer protegido hasta nueva indicación."""));

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
        return body(parameter, parameter.key(), null, level, previous, value,
                astronautName, repeatEvery, generatedAt);
    }

    /**
     * @param rangeKey clave del rango evaluado (presión: blood_pressure_systolic / _diastolic)
     * @param display  valor a mostrar (presión: "150/95"); null = el número formateado
     * @param value    valor numérico del componente que decidió el nivel
     */
    public String body(HealthParameter parameter, String rangeKey, String display, AlertLevel level,
                       AlertLevel previous, double value, String astronautName,
                       Duration repeatEvery, Instant generatedAt) {
        String unit = parameter.unit();
        String component = component(rangeKey);
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(DATE_HOUR.format(generatedAt.atZone(zone))).append("] ")
          .append(level.isCritical() ? "URGENTE – " : "ADVERTENCIA – ")
          .append(parameter.label()).append(' ').append(adjective(parameter, level)).append(": ")
          .append(nameOr(astronautName)).append(" registra ").append(display != null ? display : number(value))
          .append(' ').append(unit);

        Optional<Double> limit = level.isBelowNormal()
                ? ranges.bound(rangeKey, HealthStatus.NORMAL, true)
                : ranges.bound(rangeKey, HealthStatus.NORMAL, false);
        if (limit.isPresent()) {
            double diff = Math.abs(value - limit.get());
            sb.append(", ").append(component).append(number(diff)).append(' ').append(unit)
              .append(level.isBelowNormal() ? " bajo el mínimo normal" : " sobre el máximo normal");
        }
        sb.append(" (rango normal ").append(component).append(ranges.normalRange(rangeKey))
          .append(' ').append(unit).append("). ");

        sb.append(trend(parameter, level, previous));

        if (level.isCritical()) {
            sb.append("Es un nivel crítico y requiere atención inmediata: siga la guía adjunta y, "
                    + "si no mejora, contacte al médico de vuelo. ");
        } else {
            sb.append("Aún no es crítico, pero conviene vigilarlo y seguir la guía adjunta. ");
            thresholdText(rangeKey, level, unit).ifPresent(t -> sb.append("Si ").append(component).append(t)
                    .append(" se enviará una alerta urgente. "));
        }

        sb.append("Detectado a las ").append(HOUR.format(generatedAt.atZone(zone)))
          .append(" (").append(zone.getId()).append("). Se volverá a avisar cada ").append(human(repeatEvery))
          .append(" mientras siga en este nivel.");
        return sb.toString();
    }

    public String annex(HealthParameter parameter, AlertLevel level) {
        String guide = GUIDES.get(key(parameter, level));
        if (guide != null) {
            return guide;
        }
        return "Guía – " + parameter.label() + " " + adjective(parameter, level) + "\n"
                + "1. Confirmar la medición.\n"
                + "2. Informar al médico de vuelo y seguir sus indicaciones.";
    }

    /** "muy alto", "alta", "muy bajas"... según el género / número del parámetro. */
    static String adjective(HealthParameter parameter, AlertLevel level) {
        String base = level.label();
        return switch (parameter) {
            case GLUCOSE, TEMPERATURE, BLOOD_PRESSURE, HRV, RADIATION -> base.substring(0, base.length() - 1) + "a";
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

    /**
     * Desde qué valor una advertencia pasa a urgente, según health-ranges.json:
     * "supera 115 bpm", "llega a 200 mg/dL o más", "baja de 70 mg/dL", "llega a 25 / 100 o menos".
     * Vacío si ese nivel no es urgente para el parámetro (p. ej. Body Battery alto).
     */
    private Optional<String> thresholdText(String rangeKey, AlertLevel level, String unit) {
        HealthStatus critical = level.isBelowNormal() ? HealthStatus.VERY_LOW : HealthStatus.VERY_HIGH;
        if (ranges.severity(rangeKey, critical) != RangeSeverity.CRITICAL) {
            return Optional.empty();
        }
        boolean below = level.isBelowNormal();
        Optional<Double> limit = ranges.bound(rangeKey, critical, !below);
        if (limit.isEmpty()) {
            return Optional.empty();
        }
        boolean inclusive = ranges.inclusive(rangeKey, critical, !below);
        String v = number(limit.get()) + " " + unit;
        if (below) {
            return Optional.of(inclusive ? "llega a " + v + " o menos" : "baja de " + v);
        }
        return Optional.of(inclusive ? "llega a " + v + " o más" : "supera " + v);
    }

    /** "sistólica " / "diastólica " para la presión arterial; vacío para el resto. */
    private static String component(String rangeKey) {
        if (HealthParameter.SYSTOLIC_KEY.equals(rangeKey)) {
            return "sistólica ";
        }
        if (HealthParameter.DIASTOLIC_KEY.equals(rangeKey)) {
            return "diastólica ";
        }
        return "";
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
