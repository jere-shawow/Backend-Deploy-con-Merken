package com.astronaut.backend.reports.model;

import java.util.List;

/** Informe completo: datos generales + mediciones + comentarios. */
public record ReportDocument(ReportMeta meta, List<AstronautReading> readings, List<ReportComment> comments) {
}
