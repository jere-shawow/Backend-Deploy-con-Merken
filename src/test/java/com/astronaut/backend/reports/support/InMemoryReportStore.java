package com.astronaut.backend.reports.support;

import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import com.astronaut.backend.reports.store.ReportStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** "DynamoDB" falso para probar el cambio a modo local: se puede apagar o hacer fallar. */
public class InMemoryReportStore implements ReportStore {

    public volatile boolean available = false;
    public volatile boolean failOperations = false;
    private final Map<String, ReportDocument> docs = new LinkedHashMap<>();

    private void check() {
        if (failOperations) {
            throw new IllegalStateException("Unable to execute HTTP request: Connect timed out");
        }
    }

    @Override public boolean ping() { return available; }

    @Override
    public synchronized void createIfAbsent(ReportMeta meta) {
        check();
        docs.putIfAbsent(meta.reportId(), new ReportDocument(meta, new ArrayList<>(), new ArrayList<>()));
    }

    @Override
    public synchronized void addReadings(ReportMeta meta, List<AstronautReading> readings, Instant updatedAt) {
        check();
        createIfAbsent(meta);
        docs.get(meta.reportId()).readings().addAll(readings);
    }

    @Override
    public synchronized void addComments(ReportMeta meta, List<ReportComment> comments) {
        check();
        createIfAbsent(meta);
        docs.get(meta.reportId()).comments().addAll(comments);
    }

    @Override public synchronized Optional<ReportDocument> load(String id) { check(); return Optional.ofNullable(docs.get(id)); }
    @Override public synchronized List<ReportMeta> list() { check(); return docs.values().stream().map(ReportDocument::meta).toList(); }
    @Override public synchronized boolean delete(String id) { check(); return docs.remove(id) != null; }

    public synchronized int size() { return docs.size(); }
}
