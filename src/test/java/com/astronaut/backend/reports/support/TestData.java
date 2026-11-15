package com.astronaut.backend.reports.support;

import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportMeta;

import java.time.Instant;
import java.time.LocalDate;

public final class TestData {

    private TestData() {
    }

    public static AstronautData astronaut(long id, String name, int heartRate) {
        return new AstronautData(id, name, 0.72, 408_000.0, "118/76", heartRate, 92.5, 36.7, 7.5, 2600,
                98.0, 64, 48.0, "BUENO", true, Instant.parse("2026-11-14T14:59:00Z"));
    }

    public static AstronautReading reading(long id, String name, int heartRate, Instant at) {
        return new AstronautReading(at, astronaut(id, name, heartRate));
    }

    public static ReportComment comment(long notificationId, long astronautId, Instant at) {
        return new ReportComment(notificationId, at, astronautId, "Valentina", "PULSE", "VERY_HIGH", 131.0,
                "ALERTA: Pulso muy alto – Valentina", "[14-11-2026 12:00] Pulso muy alto: 131 bpm");
    }

    public static ReportMeta meta(String id) {
        Instant now = Instant.parse("2026-11-14T15:00:00Z");
        return new ReportMeta(id, LocalDate.of(2026, 11, 14), ReportMeta.AUTOMATIC, now, now, 0, 0, 0, 1_900_000_000L);
    }
}
