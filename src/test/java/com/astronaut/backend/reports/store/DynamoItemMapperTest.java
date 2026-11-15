package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportMeta;
import com.astronaut.backend.reports.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Conversión a la estructura NO relacional de DynamoDB (ida y vuelta, sin perder datos). */
class DynamoItemMapperTest {

    private final ReportMeta meta = TestData.meta("AUTO-2026-11-14");

    @Test
    @DisplayName("una medición guarda TODOS los datos del astronauta como documento anidado")
    void readingIsNestedDocument() {
        AstronautReading reading = TestData.reading(7, "Valentina", 131, Instant.parse("2026-11-14T15:10:00Z"));

        Map<String, AttributeValue> item = DynamoItemMapper.readingItem(meta, reading);

        assertThat(item.get("PK").s()).isEqualTo("REPORT#AUTO-2026-11-14");
        assertThat(item.get("SK").s()).isEqualTo("READING#2026-11-14T15:10:00Z#0000000007");
        assertThat(item.get("entityType").s()).isEqualTo("READING");
        assertThat(item.get("ttl").n()).isEqualTo("1900000000");

        Map<String, AttributeValue> astronaut = item.get("astronaut").m();
        assertThat(astronaut.get("name").s()).isEqualTo("Valentina");
        assertThat(astronaut.get("garmin").m().get("linked").bool()).isTrue();

        Map<String, AttributeValue> vitals = item.get("vitals").m();
        assertThat(vitals.get("heartRate").n()).isEqualTo("131");
        assertThat(vitals.get("bodyBattery").n()).isEqualTo("64");
        assertThat(vitals.get("hrv").n()).isEqualTo("48");
        assertThat(vitals.get("bloodPressure").s()).isEqualTo("118/76");
        assertThat(item.get("lifestyle").m().get("sleepHours").n()).isEqualTo("7.5");
        assertThat(item.get("mission").m().get("distanceFromEarth").n()).isEqualTo("408000");
    }

    @Test
    @DisplayName("ida y vuelta de una medición: los 16 datos del astronauta se conservan")
    void readingRoundTrip() {
        AstronautReading original = TestData.reading(7, "Valentina", 131, Instant.parse("2026-11-14T15:10:00Z"));

        AstronautReading back = DynamoItemMapper.readingFrom(DynamoItemMapper.readingItem(meta, original));

        assertThat(back).isEqualTo(original);
    }

    @Test
    @DisplayName("los valores nulos no se guardan y vuelven como null")
    void nullsAreOmitted() {
        AstronautData partial = new AstronautData(3L, "Sin datos", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        AstronautReading reading = new AstronautReading(Instant.parse("2026-11-14T15:10:00Z"), partial);

        Map<String, AttributeValue> item = DynamoItemMapper.readingItem(meta, reading);

        assertThat(item.get("vitals").m()).isEmpty();
        assertThat(DynamoItemMapper.readingFrom(item)).isEqualTo(reading);
    }

    @Test
    @DisplayName("comentario (notificación del día) con su hora: ida y vuelta")
    void commentRoundTrip() {
        ReportComment comment = TestData.comment(42, 7, Instant.parse("2026-11-14T15:00:00Z"));

        Map<String, AttributeValue> item = DynamoItemMapper.commentItem(meta, comment);

        assertThat(item.get("SK").s()).isEqualTo("COMMENT#2026-11-14T15:00:00Z#0000000042");
        assertThat(item.get("time").s()).isEqualTo("2026-11-14T15:00:00Z");
        assertThat(DynamoItemMapper.commentFrom(item)).isEqualTo(comment);
    }

    @Test
    @DisplayName("META: cuenta astronautas y comentarios desde los conjuntos de ids")
    void metaWithSets() {
        Map<String, AttributeValue> item = new java.util.HashMap<>(DynamoItemMapper.metaItem(meta));
        item.put("astronautIds", AttributeValue.builder().ns(List.of("1", "2")).build());
        item.put("commentIds", AttributeValue.builder().ns(List.of("10", "11", "12")).build());

        ReportMeta back = DynamoItemMapper.metaFrom(item);

        assertThat(back.reportId()).isEqualTo("AUTO-2026-11-14");
        assertThat(back.astronautCount()).isEqualTo(2);
        assertThat(back.commentCount()).isEqualTo(3);
        assertThat(back.expiresAtEpoch()).isEqualTo(1_900_000_000L);
    }

    @Test
    @DisplayName("números sin notación científica")
    void plainNumbers() {
        assertThat(DynamoItemMapper.n(0.0001).n()).isEqualTo("0.0001");
        assertThat(DynamoItemMapper.n(1.0E7).n()).isEqualTo("10000000");
    }
}
