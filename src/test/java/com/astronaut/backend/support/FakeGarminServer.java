package com.astronaut.backend.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP que imita la Garmin Connect Personal API (solo los endpoints que usa la app).
 * Usa el HttpServer del JDK: no requiere dependencias extra.
 *
 * Reproduce el comportamiento real relevante:
 *  - el token se entrega como JWT en text/plain;
 *  - si el cliente exige "Accept: application/json" al pedir el token, responde 406
 *    (el error que impedía vincular dispositivos);
 *  - API key inválida -> 401; Bearer inválido -> 401.
 */
public class FakeGarminServer implements AutoCloseable {

    public static final String VALID_KEY = "GCDC_test-valid-key";
    public static final String TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token";
    public static final String HEART_RATE_PATH = "/wellness-service/wellness/dailyHeartRate";
    public static final String SLEEP_PATH = "/sleep-service/sleep/dailySleep";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(32);
    private final Set<String> issuedTokens = ConcurrentHashMap.newKeySet();

    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final AtomicInteger heartRateRequests = new AtomicInteger();
    private final AtomicInteger sleepRequests = new AtomicInteger();

    private volatile Integer heartRate = 88;
    private volatile Integer sleepSeconds = 27_000; // 7,5 h
    private volatile long latencyMillis = 0;
    private volatile int forcedDataStatus = 0;     // != 0 -> los endpoints de datos responden ese código
    private volatile String lastTokenAccept;

    public FakeGarminServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(executor);
        server.createContext(TOKEN_PATH, this::handleToken);
        server.createContext(HEART_RATE_PATH, this::handleHeartRate);
        server.createContext(SLEEP_PATH, this::handleSleep);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    // ------------------------------------------------------------ control

    public FakeGarminServer heartRate(Integer bpm) { this.heartRate = bpm; return this; }
    public FakeGarminServer sleepSeconds(Integer seconds) { this.sleepSeconds = seconds; return this; }
    public FakeGarminServer latencyMillis(long millis) { this.latencyMillis = millis; return this; }
    public FakeGarminServer failDataWith(int status) { this.forcedDataStatus = status; return this; }

    /** Invalida los tokens emitidos (simula expiración del lado de Garmin). */
    public void revokeTokens() { issuedTokens.clear(); }

    public void reset() {
        heartRate = 88;
        sleepSeconds = 27_000;
        latencyMillis = 0;
        forcedDataStatus = 0;
        lastTokenAccept = null;
        issuedTokens.clear();
        tokenRequests.set(0);
        heartRateRequests.set(0);
        sleepRequests.set(0);
    }

    public int tokenRequests() { return tokenRequests.get(); }
    public int heartRateRequests() { return heartRateRequests.get(); }
    public int sleepRequests() { return sleepRequests.get(); }
    public String lastTokenAccept() { return lastTokenAccept; }

    // ---------------------------------------------------------- handlers

    private void handleToken(HttpExchange ex) throws IOException {
        tokenRequests.incrementAndGet();
        delay();
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "");
            return;
        }
        String accept = ex.getRequestHeaders().getFirst("Accept");
        lastTokenAccept = accept;
        if (accept != null && !accept.contains("*/*") && !accept.contains("text/plain")) {
            send(ex, 406, "application/json", "{\"error\":\"Not Acceptable\"}");
            return;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null) {
            send(ex, 400, "text/plain", "");
            return;
        }
        if (!("ApiKey " + VALID_KEY).equals(auth)) {
            send(ex, 401, "text/plain", "");
            return;
        }
        String jwt = jwt(Instant.now().plusSeconds(24 * 3600));
        issuedTokens.add(jwt);
        send(ex, 200, "text/plain", jwt);
    }

    private void handleHeartRate(HttpExchange ex) throws IOException {
        heartRateRequests.incrementAndGet();
        if (!authorizedDataRequest(ex)) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        String body = heartRate == null
                ? "{\"heartRateValues\":[],\"restingHeartRate\":null}"
                : "{\"calendarDate\":\"" + date(ex) + "\",\"restingHeartRate\":61,"
                + "\"heartRateValues\":[[" + (now - 240_000) + ",70],[" + (now - 120_000) + "," + heartRate + "],"
                + "[" + now + ",null]]}";
        send(ex, 200, "application/json", body);
    }

    private void handleSleep(HttpExchange ex) throws IOException {
        sleepRequests.incrementAndGet();
        if (!authorizedDataRequest(ex)) {
            return;
        }
        String body = sleepSeconds == null
                ? "{\"dailySleepDTO\":{\"sleepTimeSeconds\":null}}"
                : "{\"dailySleepDTO\":{\"calendarDate\":\"" + date(ex) + "\",\"sleepTimeSeconds\":" + sleepSeconds + "}}";
        send(ex, 200, "application/json", body);
    }

    private boolean authorizedDataRequest(HttpExchange ex) throws IOException {
        delay();
        if (forcedDataStatus != 0) {
            send(ex, forcedDataStatus, "application/json", "{\"error\":\"forced\"}");
            return false;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ") || !issuedTokens.contains(auth.substring(7))) {
            send(ex, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return false;
        }
        if (date(ex) == null) {
            send(ex, 400, "application/json", "{\"error\":\"date required\"}");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------- utils

    /** JWT sin firma real con claim exp (suficiente para el cliente). */
    public static String jwt(Instant exp) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"test\",\"jti\":\"" + UUID.randomUUID()
                + "\",\"exp\":" + exp.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    private static String date(HttpExchange ex) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            if (pair.startsWith("date=")) {
                return pair.substring(5);
            }
        }
        return null;
    }

    private void delay() {
        if (latencyMillis > 0) {
            try {
                Thread.sleep(latencyMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
