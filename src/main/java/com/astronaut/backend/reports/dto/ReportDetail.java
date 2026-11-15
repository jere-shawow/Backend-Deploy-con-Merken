package com.astronaut.backend.reports.dto;

import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;

import java.util.List;

/**
 * Informe completo:
 *  - report: datos generales
 *  - astronauts: resumen por astronauta
 *  - readings: todas las mediciones (datos completos de cada astronauta)
 *  - comments: sección de COMENTARIOS = notificaciones entregadas en el día con su hora
 */
public record ReportDetail(
        ReportSummary report,
        List<AstronautReportStats> astronauts,
        List<AstronautReading> readings,
        List<ReportComment> comments) {
}
