package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import com.astronaut.backend.reports.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalReportStoreTest {

    @TempDir
    Path dir;

    private LocalReportStore store() {
        return new LocalReportStore(dir, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("guarda mediciones y comentarios en un JSON por informe, y los cuenta")
    void storesAndCounts() {
        LocalReportStore store = store();
        ReportMeta meta = TestData.meta("AUTO-2026-11-14");
        Instant t1 = Instant.parse("2026-11-14T15:00:00Z");

        store.createIfAbsent(meta);
        store.addReadings(meta, List.of(TestData.reading(1, "A", 70, t1), TestData.reading(2, "B", 80, t1)), t1);
        store.addReadings(meta, List.of(TestData.reading(1, "A", 75, t1.plusSeconds(600))), t1.plusSeconds(600));
        store.addComments(meta, List.of(TestData.comment(10, 1, t1)));
        store.addComments(meta, List.of(TestData.comment(10, 1, t1), TestData.comment(11, 2, t1.plusSeconds(60))));

        ReportDocument doc = store.load("AUTO-2026-11-14").orElseThrow();
        assertThat(doc.readings()).hasSize(3);
        assertThat(doc.comments()).hasSize(2); // la notificación 10 no se duplica
        assertThat(doc.meta().readingCount()).isEqualTo(3);
        assertThat(doc.meta().astronautCount()).isEqualTo(2);
        assertThat(doc.meta().commentCount()).isEqualTo(2);
        assertThat(doc.meta().updatedAt()).isEqualTo(t1.plusSeconds(600));
        assertThat(dir.resolve("AUTO-2026-11-14.json")).exists();
    }

    @Test
    @DisplayName("los datos sobreviven a un reinicio (otra instancia lee los mismos archivos)")
    void persistsAcrossInstances() {
        ReportMeta meta = TestData.meta("AUTO-2026-11-14");
        store().addReadings(meta, List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());

        assertThat(store().list()).extracting(ReportMeta::reportId).containsExactly("AUTO-2026-11-14");
        assertThat(store().load("AUTO-2026-11-14").orElseThrow().readings().get(0).astronaut().hrv()).isEqualTo(48.0);
    }

    @Test
    @DisplayName("eliminar")
    void delete() {
        LocalReportStore store = store();
        store.createIfAbsent(TestData.meta("X"));

        assertThat(store.delete("X")).isTrue();
        assertThat(store.delete("X")).isFalse();
        assertThat(store.load("X")).isEmpty();
    }
}
