package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.model.AstronautData;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportMeta;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conversión entre el modelo Java y la estructura NO relacional de DynamoDB.
 *
 * Diseño de tabla única (partition key PK, sort key SK):
 *
 *   PK = REPORT#{reportId}  SK = META                               -> datos generales del informe
 *   PK = REPORT#{reportId}  SK = READING#{instante}#{astronautId}   -> una medición
 *   PK = REPORT#{reportId}  SK = COMMENT#{instante}#{notificationId} -> una notificación del día
 *
 * Cada medición guarda TODOS los datos del astronauta como documento anidado:
 * <pre>
 * {
 *   "astronaut": { "id", "name", "mood", "garmin": { "linked", "lastSync" } },
 *   "vitals":    { "heartRate", "bloodPressure", "bloodSugar", "bodyTemperature",
 *                  "oxygenInBlood", "hrv", "bodyBattery" },
 *   "lifestyle": { "sleepHours", "nutrition" },
 *   "mission":   { "spaceRadiation", "distanceFromEarth" }
 * }
 * </pre>
 * Los valores nulos no se guardan (DynamoDB no los necesita).
 */
public final class DynamoItemMapper {

    public static final String PK = "PK";
    public static final String SK = "SK";
    public static final String META_SK = "META";
    public static final String READING_PREFIX = "READING#";
    public static final String COMMENT_PREFIX = "COMMENT#";
    public static final String TTL = "ttl";

    private DynamoItemMapper() {
    }

    public static String pk(String reportId) {
        return "REPORT#" + reportId;
    }

    public static Map<String, AttributeValue> key(String reportId, String sk) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(PK, s(pk(reportId)));
        key.put(SK, s(sk));
        return key;
    }

    // ----------------------------------------------------------------- META

    public static Map<String, AttributeValue> metaItem(ReportMeta meta) {
        Map<String, AttributeValue> item = key(meta.reportId(), META_SK);
        item.put("entityType", s("REPORT"));
        item.put("reportId", s(meta.reportId()));
        item.put("reportDate", s(meta.reportDate().toString()));
        item.put("reportType", s(meta.reportType()));
        item.put("createdAt", s(meta.createdAt().toString()));
        item.put("updatedAt", s(meta.updatedAt().toString()));
        item.put("readingCount", n(meta.readingCount()));
        item.put(TTL, n(meta.expiresAtEpoch()));
        return item;
    }

    public static ReportMeta metaFrom(Map<String, AttributeValue> item) {
        return new ReportMeta(
                str(item, "reportId"),
                LocalDate.parse(str(item, "reportDate")),
                str(item, "reportType"),
                Instant.parse(str(item, "createdAt")),
                Instant.parse(str(item, "updatedAt")),
                longOr(item, "readingCount", 0),
                item.containsKey("astronautIds") ? item.get("astronautIds").ns().size() : 0,
                item.containsKey("commentIds") ? item.get("commentIds").ns().size() : 0,
                longOr(item, TTL, 0));
    }

    // -------------------------------------------------------------- READING

    public static String readingSk(AstronautReading r) {
        long id = r.astronaut().id() == null ? 0 : r.astronaut().id();
        return READING_PREFIX + r.capturedAt() + "#" + String.format("%010d", id);
    }

    public static Map<String, AttributeValue> readingItem(ReportMeta meta, AstronautReading r) {
        AstronautData a = r.astronaut();
        Map<String, AttributeValue> item = key(meta.reportId(), readingSk(r));
        item.put("entityType", s("READING"));
        item.put("reportId", s(meta.reportId()));
        item.put("reportDate", s(meta.reportDate().toString()));
        item.put("capturedAt", s(r.capturedAt().toString()));
        item.put(TTL, n(meta.expiresAtEpoch()));

        Map<String, AttributeValue> garmin = new LinkedHashMap<>();
        putBool(garmin, "linked", a.garminLinked());
        putStr(garmin, "lastSync", a.garminLastSync() == null ? null : a.garminLastSync().toString());

        Map<String, AttributeValue> astronaut = new LinkedHashMap<>();
        putNum(astronaut, "id", a.id());
        putStr(astronaut, "name", a.astronautName());
        putStr(astronaut, "mood", a.mood());
        astronaut.put("garmin", m(garmin));

        Map<String, AttributeValue> vitals = new LinkedHashMap<>();
        putNum(vitals, "heartRate", a.heartRate());
        putStr(vitals, "bloodPressure", a.bloodPressure());
        putNum(vitals, "bloodSugar", a.bloodSugar());
        putNum(vitals, "bodyTemperature", a.bodyTemperature());
        putNum(vitals, "oxygenInBlood", a.oxygenInBlood());
        putNum(vitals, "hrv", a.hrv());
        putNum(vitals, "bodyBattery", a.bodyBattery());

        Map<String, AttributeValue> lifestyle = new LinkedHashMap<>();
        putNum(lifestyle, "sleepHours", a.sleepHours());
        putNum(lifestyle, "nutrition", a.nutrition());

        Map<String, AttributeValue> mission = new LinkedHashMap<>();
        putNum(mission, "spaceRadiation", a.spaceRadiation());
        putNum(mission, "distanceFromEarth", a.distanceFromEarth());

        item.put("astronaut", m(astronaut));
        item.put("vitals", m(vitals));
        item.put("lifestyle", m(lifestyle));
        item.put("mission", m(mission));
        return item;
    }

    public static AstronautReading readingFrom(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> astronaut = map(item, "astronaut");
        Map<String, AttributeValue> garmin = map(astronaut, "garmin");
        Map<String, AttributeValue> vitals = map(item, "vitals");
        Map<String, AttributeValue> lifestyle = map(item, "lifestyle");
        Map<String, AttributeValue> mission = map(item, "mission");
        String lastSync = str(garmin, "lastSync");

        AstronautData data = new AstronautData(
                longVal(astronaut, "id"),
                str(astronaut, "name"),
                dbl(mission, "spaceRadiation"),
                dbl(mission, "distanceFromEarth"),
                str(vitals, "bloodPressure"),
                intVal(vitals, "heartRate"),
                dbl(vitals, "bloodSugar"),
                dbl(vitals, "bodyTemperature"),
                dbl(lifestyle, "sleepHours"),
                intVal(lifestyle, "nutrition"),
                dbl(vitals, "oxygenInBlood"),
                intVal(vitals, "bodyBattery"),
                dbl(vitals, "hrv"),
                str(astronaut, "mood"),
                garmin.containsKey("linked") ? garmin.get("linked").bool() : null,
                lastSync == null ? null : Instant.parse(lastSync));
        return new AstronautReading(Instant.parse(str(item, "capturedAt")), data);
    }

    // -------------------------------------------------------------- COMMENT

    public static String commentSk(ReportComment c) {
        return COMMENT_PREFIX + c.time() + "#" + String.format("%010d", c.notificationId() == null ? 0 : c.notificationId());
    }

    public static Map<String, AttributeValue> commentItem(ReportMeta meta, ReportComment c) {
        Map<String, AttributeValue> item = key(meta.reportId(), commentSk(c));
        item.put("entityType", s("COMMENT"));
        item.put("reportId", s(meta.reportId()));
        item.put("time", s(c.time().toString()));
        item.put(TTL, n(meta.expiresAtEpoch()));

        Map<String, AttributeValue> notification = new LinkedHashMap<>();
        putNum(notification, "id", c.notificationId());
        putStr(notification, "parameter", c.parameter());
        putStr(notification, "level", c.level());
        putNum(notification, "value", c.value());
        putStr(notification, "title", c.title());
        putStr(notification, "body", c.body());

        Map<String, AttributeValue> astronaut = new LinkedHashMap<>();
        putNum(astronaut, "id", c.astronautId());
        putStr(astronaut, "name", c.astronautName());

        item.put("notification", m(notification));
        item.put("astronaut", m(astronaut));
        return item;
    }

    public static ReportComment commentFrom(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> notification = map(item, "notification");
        Map<String, AttributeValue> astronaut = map(item, "astronaut");
        return new ReportComment(
                longVal(notification, "id"),
                Instant.parse(str(item, "time")),
                longVal(astronaut, "id"),
                str(astronaut, "name"),
                str(notification, "parameter"),
                str(notification, "level"),
                dbl(notification, "value"),
                str(notification, "title"),
                str(notification, "body"));
    }

    // -------------------------------------------------------------- helpers

    public static AttributeValue s(String v) { return AttributeValue.builder().s(v).build(); }
    /** Números como texto plano (sin notación científica). */
    public static AttributeValue n(Number v) {
        String text = (v instanceof Double || v instanceof Float)
                ? java.math.BigDecimal.valueOf(v.doubleValue()).stripTrailingZeros().toPlainString()
                : v.toString();
        return AttributeValue.builder().n(text).build();
    }
    static AttributeValue m(Map<String, AttributeValue> v) { return AttributeValue.builder().m(v).build(); }

    private static void putStr(Map<String, AttributeValue> map, String k, String v) {
        if (v != null) map.put(k, s(v));
    }

    private static void putNum(Map<String, AttributeValue> map, String k, Number v) {
        if (v != null) map.put(k, n(v));
    }

    private static void putBool(Map<String, AttributeValue> map, String k, Boolean v) {
        if (v != null) map.put(k, AttributeValue.builder().bool(v).build());
    }

    private static Map<String, AttributeValue> map(Map<String, AttributeValue> item, String k) {
        AttributeValue v = item.get(k);
        return v == null || !v.hasM() ? Map.of() : v.m();
    }

    static String str(Map<String, AttributeValue> item, String k) {
        AttributeValue v = item.get(k);
        return v == null ? null : v.s();
    }

    private static Double dbl(Map<String, AttributeValue> item, String k) {
        AttributeValue v = item.get(k);
        return v == null || v.n() == null ? null : Double.valueOf(v.n());
    }

    private static Integer intVal(Map<String, AttributeValue> item, String k) {
        AttributeValue v = item.get(k);
        return v == null || v.n() == null ? null : Integer.valueOf(v.n());
    }

    private static Long longVal(Map<String, AttributeValue> item, String k) {
        AttributeValue v = item.get(k);
        return v == null || v.n() == null ? null : Long.valueOf(v.n());
    }

    private static long longOr(Map<String, AttributeValue> item, String k, long def) {
        Long v = longVal(item, k);
        return v == null ? def : v;
    }
}
