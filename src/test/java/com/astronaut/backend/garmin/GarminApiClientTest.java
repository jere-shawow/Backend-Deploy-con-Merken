package com.astronaut.backend.garmin;

import com.astronaut.backend.garmin.GarminApiClient.AccessToken;
import com.astronaut.backend.support.FakeGarminServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Pruebas del cliente de la Personal API contra un Garmin falso (HTTP real, sin Spring).
 */
class GarminApiClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static FakeGarminServer garmin;
    private GarminApiClient client;

    @BeforeAll
    static void startServer() {
        garmin = new FakeGarminServer();
    }

    @AfterAll
    static void stopServer() {
        garmin.close();
    }

    @BeforeEach
    void setUp() {
        garmin.reset();
        client = new GarminApiClient(garmin.baseUrl(), "America/Santiago", MAPPER);
    }

    @Nested
    @DisplayName("Token")
    class Token {

        @Test
        @DisplayName("regresión: pide el token con Accept */* (con application/json Garmin responde 406)")
        void requestsTokenAcceptingAnyContentType() {
            AccessToken token = client.obtainToken(FakeGarminServer.VALID_KEY);

            assertThat(token.value()).isNotBlank();
            assertThat(garmin.lastTokenAccept()).contains("*/*");
        }

        @Test
        @DisplayName("toma la expiración del claim exp del JWT (menos margen de 5 min)")
        void usesJwtExpiration() {
            AccessToken token = client.obtainToken(FakeGarminServer.VALID_KEY);

            Instant expected = Instant.now().plus(Duration.ofHours(24)).minus(Duration.ofMinutes(5));
            assertThat(token.expiresAt()).isCloseTo(expected, within(30, ChronoUnit.SECONDS));
            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("recorta espacios de la API key pegada")
        void trimsApiKey() {
            AccessToken token = client.obtainToken("  " + FakeGarminServer.VALID_KEY + "\n");

            assertThat(token.value()).isNotBlank();
        }

        @Test
        @DisplayName("API key inválida -> GarminException de autenticación")
        void invalidKeyIsAuthError() {
            assertThatThrownBy(() -> client.obtainToken("GCDC_wrong"))
                    .isInstanceOf(GarminException.class)
                    .hasMessageContaining("rechazó la API key")
                    .satisfies(e -> assertThat(((GarminException) e).isAuthError()).isTrue());
        }

        @Test
        @DisplayName("Garmin caído -> GarminException sin marca de autenticación")
        void unreachableServer() {
            GarminApiClient offline = new GarminApiClient("http://127.0.0.1:9", "UTC", MAPPER);

            assertThatThrownBy(() -> offline.obtainToken(FakeGarminServer.VALID_KEY))
                    .isInstanceOf(GarminException.class)
                    .satisfies(e -> assertThat(((GarminException) e).isAuthError()).isFalse());
        }

        @Test
        @DisplayName("acepta también un token en JSON con access_token y expires_in")
        void parsesJsonToken() {
            AccessToken token = client.parseToken("{\"access_token\":\"abc\",\"expires_in\":3600}");

            assertThat(token.value()).isEqualTo("abc");
            assertThat(token.expiresAt())
                    .isCloseTo(Instant.now().plusSeconds(3600 - 300), within(30, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("token sin exp -> se asume ~23 h")
        void defaultTtlWithoutExp() {
            AccessToken token = client.parseToken("opaque-token-without-dots");

            assertThat(token.expiresAt())
                    .isCloseTo(Instant.now().plus(Duration.ofHours(23)).minus(Duration.ofMinutes(5)),
                            within(30, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("respuesta vacía de token -> error")
        void emptyTokenRejected() {
            assertThatThrownBy(() -> client.parseToken("{\"other\":1}"))
                    .isInstanceOf(GarminException.class);
        }
    }

    @Nested
    @DisplayName("Datos diarios")
    class DailyData {

        @Test
        @DisplayName("lee el pulso del día y el último valor no nulo")
        void readsLatestHeartRate() {
            garmin.heartRate(93);
            AccessToken token = client.obtainToken(FakeGarminServer.VALID_KEY);

            JsonNode json = client.getDailyHeartRate(token, LocalDate.of(2026, 11, 14));

            assertThat(client.latestHeartRate(json)).contains(93);
        }

        @Test
        @DisplayName("lee las horas de sueño (sleepTimeSeconds / 3600, 1 decimal)")
        void readsSleepHours() {
            garmin.sleepSeconds(17_160); // 4,77 h
            AccessToken token = client.obtainToken(FakeGarminServer.VALID_KEY);

            JsonNode json = client.getDailySleep(token, LocalDate.of(2026, 11, 14));

            assertThat(client.sleepHours(json)).contains(4.8);
        }

        @Test
        @DisplayName("token rechazado en datos -> GarminException de autenticación")
        void invalidBearer() {
            AccessToken fake = new AccessToken("not-issued", Instant.now().plusSeconds(60));

            assertThatThrownBy(() -> client.getDailyHeartRate(fake, LocalDate.now()))
                    .isInstanceOf(GarminException.class)
                    .satisfies(e -> assertThat(((GarminException) e).isAuthError()).isTrue());
        }

        @Test
        @DisplayName("error 5xx de Garmin incluye el código en el mensaje")
        void serverErrorMessage() {
            AccessToken token = client.obtainToken(FakeGarminServer.VALID_KEY);
            garmin.failDataWith(503);

            assertThatThrownBy(() -> client.getDailySleep(token, LocalDate.now()))
                    .isInstanceOf(GarminException.class)
                    .hasMessageContaining("503");
        }
    }

    @Nested
    @DisplayName("Interpretación de JSON")
    class Parsing {

        @Test
        @DisplayName("sin muestras de pulso usa restingHeartRate")
        void fallsBackToRestingHeartRate() throws Exception {
            JsonNode json = MAPPER.readTree("{\"heartRateValues\":[],\"restingHeartRate\":64}");

            assertThat(client.latestHeartRate(json)).contains(64);
        }

        @Test
        @DisplayName("sin datos de pulso devuelve vacío")
        void noHeartRate() throws Exception {
            assertThat(client.latestHeartRate(MAPPER.readTree("{}"))).isEmpty();
            assertThat(client.latestHeartRate(null)).isEmpty();
        }

        @Test
        @DisplayName("sueño a nivel raíz (formato devuelto por la librería Python) también se entiende")
        void sleepAtRoot() throws Exception {
            JsonNode json = MAPPER.readTree("{\"sleepTimeSeconds\":27000}");

            assertThat(client.sleepHours(json)).contains(7.5);
        }

        @Test
        @DisplayName("sueño nulo devuelve vacío")
        void noSleep() throws Exception {
            assertThat(client.sleepHours(MAPPER.readTree("{\"dailySleepDTO\":{\"sleepTimeSeconds\":null}}"))).isEmpty();
        }

        @Test
        @DisplayName("jwtExpiration lee exp y tolera tokens mal formados")
        void jwtExpiration() {
            Instant exp = Instant.parse("2030-01-01T00:00:00Z");

            assertThat(client.jwtExpiration(FakeGarminServer.jwt(exp))).contains(exp);
            assertThat(client.jwtExpiration("a.b@d.c")).isEmpty();
        }
    }
}
