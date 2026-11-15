package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Almacenamiento de informes (DynamoDB o local). */
public interface ReportStore {

    /** ¿Está disponible? (DynamoDB: conexión y tabla lista; local: siempre). */
    boolean ping();

    /** Crea el informe si aún no existe. */
    void createIfAbsent(ReportMeta meta);

    /** Agrega mediciones al informe. */
    void addReadings(ReportMeta meta, List<AstronautReading> readings, Instant updatedAt);

    /** Agrega comentarios; si una notificación ya estaba, no se duplica. */
    void addComments(ReportMeta meta, List<ReportComment> comments);

    Optional<ReportDocument> load(String reportId);

    List<ReportMeta> list();

    boolean delete(String reportId);
}
