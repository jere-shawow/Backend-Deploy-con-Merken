package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.config.ReportsProperties;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decide dónde se guardan los informes:
 *  - DynamoDB si hay conexión;
 *  - si no, en local y con el aviso "No se ha podido conectar a la base de datos... trabajando de forma local".
 * Cada reports.storage-check-seconds reintenta; al recuperar la conexión sube a DynamoDB
 * lo guardado en local y vuelve a trabajar con DynamoDB.
 */
@Primary
@Component("reportStore")
public class StorageRouter implements ReportStore {

    public static final String LOCAL_MESSAGE =
            "No se ha podido conectar a la base de datos... trabajando de forma local";
    public static final String DYNAMO_MESSAGE = "Conectado a DynamoDB";
    public static final String LOCAL_CONFIGURED_MESSAGE =
            "Informes guardados en local (REPORTS_STORAGE=local)";

    private static final Logger log = LoggerFactory.getLogger(StorageRouter.class);

    public enum Mode { DYNAMODB, LOCAL }

    private final ReportStore dynamo;
    private final LocalReportStore local;
    private final ReportsProperties properties;
    private final Clock clock;

    private volatile Mode mode = Mode.LOCAL;
    private volatile String lastError;
    private volatile Instant checkedAt;

    public StorageRouter(@Qualifier("dynamoReportStore") ReportStore dynamo,
                         @Qualifier("localReportStore") LocalReportStore local,
                         ReportsProperties properties, Clock clock) {
        this.dynamo = dynamo;
        this.local = local;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        checkConnection();
    }

    /** Verifica la conexión; si volvió, migra lo local a DynamoDB. */
    @Scheduled(fixedDelayString = "${reports.storage-check-seconds:60}", initialDelayString = "${reports.storage-check-seconds:60}",
               timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public synchronized void checkConnection() {
        checkedAt = clock.instant();
        if (!properties.dynamoEnabled()) {
            if (lastError == null) {
                log.info(LOCAL_CONFIGURED_MESSAGE);
            }
            lastError = "DynamoDB desactivado por configuración";
            mode = Mode.LOCAL;
            return;
        }
        if (!dynamo.ping()) {
            if (mode != Mode.LOCAL || lastError == null) {
                log.warn(LOCAL_MESSAGE);
            }
            lastError = lastError == null ? "Sin respuesta de DynamoDB" : lastError;
            mode = Mode.LOCAL;
            return;
        }
        if (mode == Mode.LOCAL) {
            migrateLocalToDynamo();
            log.info("Conexión con DynamoDB establecida");
        }
        mode = Mode.DYNAMODB;
        lastError = null;
    }

    private void migrateLocalToDynamo() {
        for (ReportMeta meta : local.list()) {
            local.load(meta.reportId()).ifPresent(doc -> {
                dynamo.createIfAbsent(doc.meta());
                dynamo.addReadings(doc.meta(), doc.readings(), doc.meta().updatedAt());
                dynamo.addComments(doc.meta(), doc.comments());
                local.delete(meta.reportId());
                log.info("Informe {} subido de local a DynamoDB", meta.reportId());
            });
        }
    }

    public StorageStatus status() {
        boolean connected = mode == Mode.DYNAMODB;
        int localCount;
        try {
            localCount = local.list().size();
        } catch (RuntimeException e) {
            localCount = 0;
        }
        String message = connected ? DYNAMO_MESSAGE
                : properties.dynamoEnabled() ? LOCAL_MESSAGE : LOCAL_CONFIGURED_MESSAGE;
        return new StorageStatus(mode.name(), connected, message,
                properties.dynamodb().table(), properties.dynamodb().region(), localCount, lastError, checkedAt);
    }

    public Mode mode() {
        return mode;
    }

    // ------------------------------------------------- operaciones con respaldo

    /** Ejecuta en DynamoDB; si falla, pasa a modo local y repite la operación en local. */
    private <T> T run(Function<ReportStore, T> op) {
        if (mode == Mode.DYNAMODB) {
            try {
                return op.apply(dynamo);
            } catch (RuntimeException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                mode = Mode.LOCAL;
                log.warn("{} ({})", LOCAL_MESSAGE, lastError);
            }
        }
        return op.apply(local);
    }

    @Override
    public boolean ping() {
        return mode == Mode.DYNAMODB;
    }

    @Override
    public void createIfAbsent(ReportMeta meta) {
        run(s -> { s.createIfAbsent(meta); return null; });
    }

    @Override
    public void addReadings(ReportMeta meta, List<AstronautReading> readings, Instant updatedAt) {
        run(s -> { s.createIfAbsent(meta); s.addReadings(meta, readings, updatedAt); return null; });
    }

    @Override
    public void addComments(ReportMeta meta, List<ReportComment> comments) {
        run(s -> { s.createIfAbsent(meta); s.addComments(meta, comments); return null; });
    }

    @Override
    public Optional<ReportDocument> load(String reportId) {
        return run(s -> s.load(reportId));
    }

    @Override
    public List<ReportMeta> list() {
        return run(ReportStore::list);
    }

    @Override
    public boolean delete(String reportId) {
        return run(s -> s.delete(reportId));
    }
}
