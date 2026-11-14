package com.astronaut.backend.performance;

import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.GarminLink;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.astronaut.backend.service.GarminSyncService;
import com.astronaut.backend.support.FakeGarminServer;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de rendimiento del backend (servidor HTTP real en puerto aleatorio).
 * Se ejecutan con:  mvn verify -Pperformance
 *
 * Presupuestos (ajustables por propiedad del sistema):
 *  - perf.p95.ms        p95 de GET /api/astronauts con 50 clientes concurrentes   (default 250 ms)
 *  - perf.sync.budget.ms una vuelta de sync para 25 astronautas con Garmin a 20 ms (default 5000 ms
 *                        = el intervalo por defecto: si una vuelta tarda más que el intervalo,
 *                        el "tiempo real" se atrasa)
 */
@Tag("performance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:performance-it;DB_CLOSE_DELAY=-1")
@DirtiesContext
@ActiveProfiles("test")
class BackendPerformanceIT {

    private static final FakeGarminServer GARMIN = new FakeGarminServer();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("garmin.base-url", GARMIN::baseUrl);
    }

    @AfterAll
    static void stop() {
        GARMIN.close();
    }

    @LocalServerPort private int port;
    @Autowired private AstronautRepository astronauts;
    @Autowired private GarminLinkRepository links;
    @Autowired private GarminSyncService syncService;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void clean() {
        GARMIN.reset();
        links.deleteAll();
        astronauts.deleteAll();
    }

    @Test
    @DisplayName("dashboard: 50 clientes refrescando la lista de 100 astronautas, p95 dentro del presupuesto")
    void dashboardPollingLatency() throws Exception {
        for (int i = 0; i < 100; i++) {
            astronauts.save(TestData.astronaut("Astronauta " + i));
        }
        int clients = 50;
        int requestsPerClient = 40;
        long p95Budget = Long.getLong("perf.p95.ms", 250);

        // calentamiento (JIT, pool de conexiones)
        for (int i = 0; i < 50; i++) {
            get("/api/astronauts");
        }

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int c = 0; c < clients; c++) {
            tasks.add(() -> {
                int errors = 0;
                for (int r = 0; r < requestsPerClient; r++) {
                    long start = System.nanoTime();
                    int status = get("/api/astronauts");
                    latencies.add((System.nanoTime() - start) / 1_000_000);
                    if (status != 200) {
                        errors++;
                    }
                }
                return errors;
            });
        }
        long wallStart = System.nanoTime();
        int errors = 0;
        for (Future<Integer> f : pool.invokeAll(tasks)) {
            errors += f.get();
        }
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdown();

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);
        double throughput = sorted.size() * 1000.0 / Math.max(1, wallMs);
        System.out.printf("[perf] GET /api/astronauts x%d  p50=%dms p95=%dms p99=%dms  %.0f req/s  errores=%d%n",
                sorted.size(), p50, p95, p99, throughput, errors);

        assertThat(errors).isZero();
        assertThat(p95).as("p95 en ms").isLessThanOrEqualTo(p95Budget);
    }

    @Test
    @DisplayName("sincronización Garmin: una vuelta para 25 astronautas cabe en el intervalo de 5 s")
    void garminSyncFitsInRefreshInterval() {
        int linked = 25;
        long budget = Long.getLong("perf.sync.budget.ms", 5000);
        GARMIN.latencyMillis(20);
        for (int i = 0; i < linked; i++) {
            Astronaut a = astronauts.save(TestData.astronaut("Garmin " + i));
            links.save(new GarminLink(a.getId(), FakeGarminServer.VALID_KEY));
        }
        syncService.syncAll(); // primera vuelta: incluye tokens y sueño

        long start = System.nanoTime();
        syncService.syncAll(); // vuelta típica: solo pulso
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("[perf] syncAll con %d vinculados (Garmin 20 ms): %d ms (presupuesto %d ms)%n",
                linked, elapsedMs, budget);
        assertThat(elapsedMs).as("duración de una vuelta de sync en ms").isLessThanOrEqualTo(budget);
        assertThat(GARMIN.tokenRequests()).as("tokens reutilizados entre vueltas").isEqualTo(linked);
    }

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
