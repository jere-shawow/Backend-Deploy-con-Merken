package com.astronaut.backend.reports.service;

import com.astronaut.backend.reports.client.AstronautDataSource;
import com.astronaut.backend.reports.client.NotificationSource;
import com.astronaut.backend.reports.config.ReportsProperties;
import com.astronaut.backend.reports.dto.AstronautReportStats;
import com.astronaut.backend.reports.dto.MetricStats;
import com.astronaut.backend.reports.dto.ReportDetail;
import com.astronaut.backend.reports.dto.ReportSummary;
import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.exception.ServiceUnavailableException;
import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import com.astronaut.backend.reports.store.StorageRouter;
import com.astronaut.backend.reports.store.StorageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Informes diarios:
 *  - captureSnapshot(): cada 10 min agrega los datos COMPLETOS de todos los astronautas
 *    y las notificaciones del día (comentarios) al informe automático del día.
 *  - generateManual(): informe con lo acumulado hoy + los datos actuales + comentarios.
 *  - purgeExpired(): borra los informes con más días que la retención configurada en la app.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    /** TTL de DynamoDB = red de seguridad (la retención real la aplica purgeExpired). */
    private static final int MAX_RETENTION_DAYS = 365;

    private final StorageRouter store;
    private final AstronautDataSource astronauts;
    private final NotificationSource notifications;
    private final ReportsProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(StorageRouter store, AstronautDataSource astronauts, NotificationSource notifications,
                         ReportsProperties properties, Clock clock) {
        this.store = store;
        this.astronauts = astronauts;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.timeZone() == null ? "America/Santiago" : properties.timeZone());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // -------------------------------------------------------------- escritura

    /** Agrega una medición de todos los astronautas + comentarios al informe automático de hoy. */
    public ReportSummary captureSnapshot() {
        Instant now = clock.instant();
        LocalDate today = today();
        ReportMeta meta = newMeta("AUTO-" + today, today, ReportMeta.AUTOMATIC, now);

        store.createIfAbsent(meta);
        store.addReadings(meta, currentReadings(now), now);
        store.addComments(meta, todaysComments(today));
        return summaryOf(store.load(meta.reportId()).orElseThrow().meta());
    }

    /** Informe manual con la información del día hasta este momento. */
    public ReportDetail generateManual() {
        Instant now = clock.instant();
        LocalDate today = today();
        String id = "MANUAL-" + ID_TIME.format(now.atZone(zone)) + "-" + UUID.randomUUID().toString().substring(0, 4);
        ReportMeta meta = newMeta(id, today, ReportMeta.MANUAL, now);

        List<AstronautReading> readings = new ArrayList<>();
        List<ReportComment> comments = new ArrayList<>();
        store.load("AUTO-" + today).ifPresent(auto -> {
            readings.addAll(auto.readings());
            comments.addAll(auto.comments());
        });
        readings.addAll(currentReadings(now));
        comments.addAll(todaysComments(today));

        store.createIfAbsent(meta);
        store.addReadings(meta, readings, now);
        store.addComments(meta, comments);
        return get(id);
    }

    public void delete(String id) {
        if (!store.delete(id)) {
            throw new ResourceNotFoundException("No existe un informe con id " + id);
        }
    }

    /** Borra los informes más antiguos que la retención (días) configurada en la app. */
    public int purgeExpired() {
        int days = retentionDays();
        LocalDate cutoff = today().minusDays(days);
        int deleted = 0;
        for (ReportMeta meta : store.list()) {
            if (meta.reportDate().isBefore(cutoff) && store.delete(meta.reportId())) {
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Se eliminaron {} informe(s) anteriores a {} (retención {} días)", deleted, cutoff, days);
        }
        return deleted;
    }

    // ---------------------------------------------------------------- lectura

    public List<ReportSummary> list() {
        return store.list().stream()
                .sorted(Comparator.comparing(ReportMeta::reportDate).reversed()
                        .thenComparing(ReportMeta::createdAt, Comparator.reverseOrder()))
                .map(this::summaryOf)
                .toList();
    }

    public ReportDetail get(String id) {
        ReportDocument doc = store.load(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un informe con id " + id));
        Map<Long, List<AstronautReading>> byAstronaut = new LinkedHashMap<>();
        for (AstronautReading r : doc.readings()) {
            byAstronaut.computeIfAbsent(r.astronaut().id(), k -> new ArrayList<>()).add(r);
        }
        List<AstronautReportStats> stats = byAstronaut.values().stream()
                .map(list -> statsOf(list, doc.comments()))
                .toList();
        return new ReportDetail(summaryOf(doc.meta()), stats, doc.readings(), doc.comments());
    }

    public StorageStatus storageStatus() {
        return store.status();
    }

    /** CSV con dos secciones: MEDICIONES y COMENTARIOS (notificaciones del día con su hora). */
    public String toCsv(String id) {
        ReportDetail d = get(id);
        StringBuilder sb = new StringBuilder();
        sb.append("INFORME;").append(d.report().id()).append(';').append(d.report().reportDate())
          .append(';').append(d.report().type()).append("\n\n");
        sb.append("MEDICIONES\n");
        sb.append("fecha_hora;astronauta_id;astronauta;pulso_bpm;presion;azucar_mg_dl;temperatura_c;oxigeno_pct;")
          .append("hrv_ms;body_battery;sueno_h;nutricion_kcal;radiacion_msv;distancia_km;animo;garmin\n");
        for (AstronautReading r : d.readings()) {
            AstronautData a = r.astronaut();
            sb.append(local(r.capturedAt())).append(';').append(n(a.id())).append(';').append(csv(a.astronautName()))
              .append(';').append(n(a.heartRate())).append(';').append(csv(a.bloodPressure()))
              .append(';').append(n(a.bloodSugar())).append(';').append(n(a.bodyTemperature()))
              .append(';').append(n(a.oxygenInBlood())).append(';').append(n(a.hrv()))
              .append(';').append(n(a.bodyBattery())).append(';').append(n(a.sleepHours()))
              .append(';').append(n(a.nutrition())).append(';').append(n(a.spaceRadiation()))
              .append(';').append(n(a.distanceFromEarth())).append(';').append(csv(a.mood()))
              .append(';').append(Boolean.TRUE.equals(a.garminLinked()) ? "si" : "no").append('\n');
        }
        sb.append("\nCOMENTARIOS (notificaciones del día)\n");
        sb.append("hora;astronauta;parametro;nivel;valor;titulo;detalle\n");
        for (ReportComment c : d.comments()) {
            sb.append(HOUR.format(c.time().atZone(zone))).append(';').append(csv(c.astronautName()))
              .append(';').append(csv(c.parameter())).append(';').append(csv(c.level()))
              .append(';').append(n(c.value())).append(';').append(csv(c.title()))
              .append(';').append(csv(c.body())).append('\n');
        }
        return sb.toString();
    }

    // --------------------------------------------------------------- datos

    private List<AstronautReading> currentReadings(Instant now) {
        List<AstronautData> list;
        try {
            list = astronauts.findAll();
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("No se pudo obtener los datos de los astronautas: " + e.getMessage(), e);
        }
        return list.stream().map(a -> new AstronautReading(now, a)).toList();
    }

    /** Notificaciones entregadas hoy (zona horaria de la app). Si no se pueden leer, ninguna. */
    private List<ReportComment> todaysComments(LocalDate today) {
        try {
            return notifications.findAll().stream()
                    .filter(n -> n.createdAt() != null && n.createdAt().atZone(zone).toLocalDate().equals(today))
                    .map(n -> new ReportComment(n.id(), n.createdAt(), n.astronautId(), n.astronautName(),
                            n.parameter(), n.level(), n.measuredValue(), n.title(), n.body()))
                    .sorted(Comparator.comparing(ReportComment::time))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("No se pudieron leer las notificaciones para los comentarios: {}", e.getMessage());
            return List.of();
        }
    }

    private int retentionDays() {
        try {
            return astronauts.reportRetentionDays().orElse(properties.retentionFallback());
        } catch (RuntimeException e) {
            return properties.retentionFallback();
        }
    }

    private ReportMeta newMeta(String id, LocalDate date, String type, Instant now) {
        long ttl = date.plusDays(MAX_RETENTION_DAYS + 1L).atStartOfDay(zone).toEpochSecond();
        return new ReportMeta(id, date, type, now, now, 0, 0, 0, ttl);
    }

    private ReportSummary summaryOf(ReportMeta m) {
        return new ReportSummary(m.reportId(), m.reportDate(), m.reportType(), m.createdAt(), m.updatedAt(),
                m.readingCount(), m.astronautCount(), m.commentCount(),
                m.reportDate().plusDays(retentionDays()), store.mode().name());
    }

    static AstronautReportStats statsOf(List<AstronautReading> readings, List<ReportComment> comments) {
        AstronautData last = readings.get(readings.size() - 1).astronaut();
        long alerts = comments.stream().filter(c -> Objects.equals(c.astronautId(), last.id())).count();
        return new AstronautReportStats(
                last.id(), last.astronautName(), readings.size(),
                MetricStats.of(readings.stream().map(r -> r.astronaut().heartRate()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().bloodSugar()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().bodyTemperature()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().sleepHours()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().oxygenInBlood()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().bodyBattery()).toList()),
                MetricStats.of(readings.stream().map(r -> r.astronaut().hrv()).toList()),
                last.mood(), alerts);
    }

    private String local(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(instant.atZone(zone));
    }

    private static String n(Number value) {
        return value == null ? "" : value.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\n', ' ');
        return clean.contains(";") || clean.contains("\"")
                ? "\"" + clean.replace("\"", "\"\"") + "\""
                : clean;
    }
}
