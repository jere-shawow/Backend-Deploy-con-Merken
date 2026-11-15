package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.config.ReportsProperties;
import com.astronaut.backend.reports.model.ReportMeta;
import com.astronaut.backend.reports.support.InMemoryReportStore;
import com.astronaut.backend.reports.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorageRouterTest {

    @TempDir
    Path dir;

    private InMemoryReportStore dynamo;
    private LocalReportStore local;
    private StorageRouter router;

    @BeforeEach
    void setUp() {
        dynamo = new InMemoryReportStore();
        local = new LocalReportStore(dir, new ObjectMapper().findAndRegisterModules());
        ReportsProperties props = new ReportsProperties("America/Santiago", 10, 7,
                dir.toString(), 60, new ReportsProperties.Dynamo("astronaut-reports", "us-east-1", null, null, null, true), "dynamodb");
        router = new StorageRouter(dynamo, local, props, Clock.systemUTC());
    }

    @Test
    @DisplayName("sin conexión a DynamoDB: trabaja en local y muestra el mensaje")
    void localWhenDynamoUnavailable() {
        router.checkConnection();

        StorageStatus status = router.status();
        assertThat(status.mode()).isEqualTo("LOCAL");
        assertThat(status.connected()).isFalse();
        assertThat(status.message()).isEqualTo("No se ha podido conectar a la base de datos... trabajando de forma local");

        router.addReadings(TestData.meta("R1"), List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());
        assertThat(local.load("R1")).isPresent();
        assertThat(dynamo.size()).isZero();
    }

    @Test
    @DisplayName("REPORTS_STORAGE=local: no intenta AWS aunque esté disponible")
    void configuredLocal() {
        dynamo.available = true;
        ReportsProperties props = new ReportsProperties("America/Santiago", 10, 7, dir.toString(), 60,
                new ReportsProperties.Dynamo("astronaut-reports", "us-east-1", null, null, null, true), "local");
        StorageRouter localOnly = new StorageRouter(dynamo, local, props, Clock.systemUTC());
        localOnly.checkConnection();

        assertThat(localOnly.status().mode()).isEqualTo("LOCAL");
        assertThat(localOnly.status().message()).isEqualTo("Informes guardados en local (REPORTS_STORAGE=local)");
        localOnly.addReadings(TestData.meta("R9"), List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());
        assertThat(local.load("R9")).isPresent();
        assertThat(dynamo.size()).isZero();
    }

    @Test
    @DisplayName("con conexión: guarda en DynamoDB")
    void dynamoWhenAvailable() {
        dynamo.available = true;
        router.checkConnection();

        router.addReadings(TestData.meta("R1"), List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());

        assertThat(router.status().mode()).isEqualTo("DYNAMODB");
        assertThat(router.status().message()).isEqualTo("Conectado a DynamoDB");
        assertThat(dynamo.load("R1")).isPresent();
        assertThat(local.list()).isEmpty();
    }

    @Test
    @DisplayName("si DynamoDB falla en plena operación, pasa a local sin perder el dato")
    void fallsBackOnFailure() {
        dynamo.available = true;
        router.checkConnection();
        dynamo.failOperations = true;

        router.addReadings(TestData.meta("R1"), List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());

        assertThat(router.status().mode()).isEqualTo("LOCAL");
        assertThat(router.status().lastError()).contains("Connect timed out");
        assertThat(local.load("R1").orElseThrow().readings()).hasSize(1);
    }

    @Test
    @DisplayName("al volver la conexión, sube a DynamoDB lo guardado en local y lo borra de local")
    void migratesWhenConnectionReturns() {
        router.checkConnection(); // local
        ReportMeta meta = TestData.meta("R1");
        router.addReadings(meta, List.of(TestData.reading(1, "A", 70, Instant.now())), Instant.now());
        router.addComments(meta, List.of(TestData.comment(5, 1, Instant.now())));

        dynamo.available = true;
        router.checkConnection();

        assertThat(router.status().mode()).isEqualTo("DYNAMODB");
        assertThat(dynamo.load("R1").orElseThrow().readings()).hasSize(1);
        assertThat(dynamo.load("R1").orElseThrow().comments()).hasSize(1);
        assertThat(local.list()).isEmpty();
        assertThat(router.status().localReports()).isZero();
    }
}
