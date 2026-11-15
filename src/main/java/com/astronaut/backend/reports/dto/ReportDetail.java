package com.astronaut.backend.reports.dto;

import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;

import java.util.List;

/**
 * Informe completo:
 *  - report: datos generales
 *  - astronauts: resumen por astronauta (mín / prom / máx)
 *  - timeline: historial por astronauta, una entrada por medición (12:00, 12:10, 12:20...)
 *  - readings: todas las mediciones en orden de hora (datos completos de cada astronauta)
 *  - comments: sección de COMENTARIOS = notificaciones entregadas en el día con su hora
 */
public record ReportDetail(
        ReportSummary report,
        List<AstronautReportStats> astronauts,
        List<AstronautTimeline> timeline,
        List<AstronautReading> readings,
        List<ReportComment> comments) {
}
