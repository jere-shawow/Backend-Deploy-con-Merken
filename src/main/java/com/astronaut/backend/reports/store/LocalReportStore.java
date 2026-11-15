package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.config.ReportsProperties;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Respaldo local: un archivo JSON por informe en reports.local-dir.
 * Se usa cuando DynamoDB no está disponible; al volver la conexión, se sube a DynamoDB.
 */
@Component("localReportStore")
public class LocalReportStore implements ReportStore {

    private final Path dir;
    private final ObjectMapper mapper;

    @Autowired
    public LocalReportStore(ReportsProperties properties, ObjectMapper objectMapper) {
        this(Path.of(properties.localDir()), objectMapper);
    }

    public LocalReportStore(Path dir, ObjectMapper objectMapper) {
        this.dir = dir;
        this.mapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta local de informes " + dir, e);
        }
    }

    public Path dir() {
        return dir;
    }

    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public synchronized void createIfAbsent(ReportMeta meta) {
        if (read(meta.reportId()).isEmpty()) {
            write(new ReportDocument(meta, new ArrayList<>(), new ArrayList<>()));
        }
    }

    @Override
    public synchronized void addReadings(ReportMeta meta, List<AstronautReading> readings, Instant updatedAt) {
        ReportDocument doc = read(meta.reportId())
                .orElseGet(() -> new ReportDocument(meta, new ArrayList<>(), new ArrayList<>()));
        List<AstronautReading> all = new ArrayList<>(doc.readings());
        all.addAll(readings);
        write(new ReportDocument(withCounts(doc.meta(), all, doc.comments(), updatedAt, meta.expiresAtEpoch()),
                all, doc.comments()));
    }

    @Override
    public synchronized void addComments(ReportMeta meta, List<ReportComment> comments) {
        ReportDocument doc = read(meta.reportId())
                .orElseGet(() -> new ReportDocument(meta, new ArrayList<>(), new ArrayList<>()));
        List<ReportComment> all = new ArrayList<>(doc.comments());
        Set<Long> known = new HashSet<>();
        all.forEach(c -> known.add(c.notificationId()));
        for (ReportComment c : comments) {
            if (c.notificationId() == null || known.add(c.notificationId())) {
                all.add(c);
            }
        }
        all.sort(Comparator.comparing(ReportComment::time));
        write(new ReportDocument(withCounts(doc.meta(), doc.readings(), all, doc.meta().updatedAt(),
                doc.meta().expiresAtEpoch()), doc.readings(), all));
    }

    @Override
    public synchronized Optional<ReportDocument> load(String reportId) {
        return read(reportId);
    }

    @Override
    public synchronized List<ReportMeta> list() {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> readFile(p).map(ReportDocument::meta).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized boolean delete(String reportId) {
        try {
            return Files.deleteIfExists(file(reportId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // -------------------------------------------------------------- helpers

    private static ReportMeta withCounts(ReportMeta m, List<AstronautReading> readings,
                                         List<ReportComment> comments, Instant updatedAt, long ttl) {
        int astronauts = (int) readings.stream().map(r -> r.astronaut().id()).filter(Objects::nonNull).distinct().count();
        return new ReportMeta(m.reportId(), m.reportDate(), m.reportType(), m.createdAt(), updatedAt,
                readings.size(), astronauts, comments.size(), ttl);
    }

    private Path file(String reportId) {
        String safe = reportId.replaceAll("[^A-Za-z0-9_.-]", "_");
        return dir.resolve(safe + ".json");
    }

    private Optional<ReportDocument> read(String reportId) {
        return readFile(file(reportId));
    }

    private Optional<ReportDocument> readFile(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(path.toFile(), ReportDocument.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Informe local dañado: " + path, e);
        }
    }

    /** Escritura atómica (archivo temporal + rename) para no dejar JSON a medias. */
    private void write(ReportDocument doc) {
        Path target = file(doc.meta().reportId());
        try {
            Path tmp = Files.createTempFile(dir, "report-", ".tmp");
            mapper.writeValue(tmp.toFile(), doc);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el informe local " + target, e);
        }
    }
}
