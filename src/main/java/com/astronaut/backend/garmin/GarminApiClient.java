package com.astronaut.backend.garmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cliente HTTP para la Garmin Connect Personal API (oficial).
 *
 * Flujo (equivalente a garmin_personal_api de Python, pero en Java):
 *  1. POST {base}/personalapitoken-service/personal-api/personal-api-key/token
 *     con "Authorization: ApiKey <key>" y "Accept: *&#47;*"  -> JWT en TEXTO PLANO (válido 24 h).
 *     Ojo: si se pide "Accept: application/json" Garmin rechaza la petición.
 *  2. GET  {base}/wellness-service/wellness/dailyHeartRate?date=YYYY-MM-DD   (Bearer)
 *  3. GET  {base}/sleep-service/sleep/dailySleep?date=YYYY-MM-DD             (Bearer)
 *  4. GET  {base}/wellness-service/wellness/bodyBattery/reports/daily?startDate=..&endDate=..  (Bearer)
 *  5. GET  {base}/hrv-service/hrv/YYYY-MM-DD                                  (Bearer)
 *  Todas las consultas de datos usan el mismo JWT obtenido en el paso 1.
 */
@Component
public class GarminApiClient {

    static final String TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token";
    static final String HEART_RATE_PATH = "/wellness-service/wellness/dailyHeartRate";
    static final String SLEEP_PATH = "/sleep-service/sleep/dailySleep";
    static final String BODY_BATTERY_PATH = "/wellness-service/wellness/bodyBattery/reports/daily";
    static final String HRV_PATH = "/hrv-service/hrv/{date}";

    /** Garmin no devuelve refresh tokens; si el JWT no trae "exp" asumimos ~23 h. */
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(23);
    /** Renovar el token unos minutos antes de que expire. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final String USER_AGENT = "astronaut-monitor/1.0";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ZoneId zoneId;

    public GarminApiClient(@Value("${garmin.base-url}") String baseUrl,
                           @Value("${garmin.time-zone}") String timeZone,
                           ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        this.objectMapper = objectMapper;
        this.zoneId = ZoneId.of(timeZone);
    }

    /** Fecha "de hoy" según la zona horaria configurada (la de la cuenta Garmin). */
    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    // ------------------------------------------------------------- token

    public record AccessToken(String value, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public AccessToken obtainToken(String apiKey) {
        String body;
        try {
            body = restClient.post()
                    .uri(TOKEN_PATH)
                    // El endpoint responde el JWT como text/plain: no restringir a JSON
                    .accept(MediaType.ALL)
                    .header("Authorization", "ApiKey " + apiKey.trim())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            boolean auth = isAuthStatus(e.getStatusCode());
            throw new GarminException(auth
                    ? "Garmin rechazó la API key (inválida, revocada o expirada) [HTTP "
                        + e.getStatusCode().value() + "]"
                    : "Garmin respondió " + e.getStatusCode().value() + " al pedir el token"
                        + snippet(e.getResponseBodyAsString()), auth, e);
        } catch (ResourceAccessException e) {
            throw new GarminException("No se pudo conectar con Garmin Connect", false, e);
        }
        if (body == null || body.isBlank()) {
            throw new GarminException("Garmin devolvió un token vacío", true);
        }
        return parseToken(body.trim());
    }

    /**
     * Garmin responde el JWT como texto plano. Por robustez también se acepta
     * un JSON con access_token. La expiración se toma del claim "exp" del JWT.
     */
    AccessToken parseToken(String body) {
        String token = body;
        Instant expiresAt = null;
        if (body.startsWith("{")) {
            try {
                JsonNode json = objectMapper.readTree(body);
                token = firstText(json, "access_token", "accessToken", "token");
                if (json.hasNonNull("expires_in")) {
                    expiresAt = Instant.now().plusSeconds(json.get("expires_in").asLong());
                }
            } catch (Exception e) {
                throw new GarminException("Respuesta de token de Garmin no reconocida", false, e);
            }
        } else if (body.startsWith("\"") && body.endsWith("\"")) {
            token = body.substring(1, body.length() - 1);
        }
        if (token == null || token.isBlank()) {
            throw new GarminException("Garmin no devolvió un token de acceso", true);
        }
        if (expiresAt == null) {
            expiresAt = jwtExpiration(token).orElse(Instant.now().plus(DEFAULT_TOKEN_TTL));
        }
        Instant renewAt = expiresAt.minus(EXPIRY_MARGIN);
        if (renewAt.isBefore(Instant.now())) {
            renewAt = Instant.now().plusSeconds(60);
        }
        return new AccessToken(token, renewAt);
    }

    /** Lee el claim "exp" (segundos epoch) del payload de un JWT, sin validar firma. */
    Optional<Instant> jwtExpiration(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(payload);
            return claims.hasNonNull("exp")
                    ? Optional.of(Instant.ofEpochSecond(claims.get("exp").asLong()))
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------- datos

    public JsonNode getDailyHeartRate(AccessToken token, LocalDate date) {
        return get(token, HEART_RATE_PATH, date);
    }

    public JsonNode getDailySleep(AccessToken token, LocalDate date) {
        return get(token, SLEEP_PATH, date);
    }

    /** Body Battery del día (lista de reportes diarios; se pide solo un día). */
    public JsonNode getDailyBodyBattery(AccessToken token, LocalDate date) {
        return call(token, BODY_BATTERY_PATH, b -> b.path(BODY_BATTERY_PATH)
                .queryParam("startDate", date.toString())
                .queryParam("endDate", date.toString())
                .build());
    }

    /** HRV de la noche que terminó en la fecha indicada. */
    public JsonNode getDailyHrv(AccessToken token, LocalDate date) {
        return call(token, HRV_PATH, b -> b.path("/hrv-service/hrv/" + date).build());
    }

    private JsonNode get(AccessToken token, String path, LocalDate date) {
        return call(token, path, b -> b.path(path).queryParam("date", date.toString()).build());
    }

    private JsonNode call(AccessToken token, String path, Function<UriBuilder, URI> uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token.value())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            boolean auth = isAuthStatus(e.getStatusCode());
            throw new GarminException("Garmin respondió " + e.getStatusCode().value() + " en " + path
                    + snippet(e.getResponseBodyAsString()), auth, e);
        } catch (ResourceAccessException e) {
            throw new GarminException("No se pudo conectar con Garmin Connect", false, e);
        }
    }

    // ---------------------------------------------------- interpretación

    /**
     * Último valor de pulso registrado en el día.
     * heartRateValues = [[timestamp, bpm], ...]; se recorre desde el final.
     * Si no hay muestras, usa restingHeartRate.
     */
    public Optional<Integer> latestHeartRate(JsonNode dailyHeartRate) {
        if (dailyHeartRate == null || dailyHeartRate.isNull()) {
            return Optional.empty();
        }
        JsonNode values = dailyHeartRate.path("heartRateValues");
        if (values.isArray()) {
            for (int i = values.size() - 1; i >= 0; i--) {
                JsonNode sample = values.get(i);
                if (sample != null && sample.isArray() && sample.size() > 1 && sample.get(1).isNumber()) {
                    return Optional.of(sample.get(1).asInt());
                }
            }
        }
        JsonNode resting = dailyHeartRate.path("restingHeartRate");
        return resting.isNumber() ? Optional.of(resting.asInt()) : Optional.empty();
    }

    /**
     * Último nivel de Body Battery del día.
     * La respuesta es una lista de reportes diarios (o un solo reporte); cada uno trae
     * bodyBatteryValuesArray = [[timestamp, nivel], ...]. Se toma el último nivel no nulo.
     */
    public Optional<Integer> latestBodyBattery(JsonNode bodyBattery) {
        if (bodyBattery == null || bodyBattery.isNull()) {
            return Optional.empty();
        }
        JsonNode report = bodyBattery.isArray()
                ? (bodyBattery.isEmpty() ? null : bodyBattery.get(bodyBattery.size() - 1))
                : bodyBattery;
        if (report == null) {
            return Optional.empty();
        }
        JsonNode values = report.path("bodyBatteryValuesArray");
        if (values.isArray()) {
            for (int i = values.size() - 1; i >= 0; i--) {
                JsonNode sample = values.get(i);
                if (sample != null && sample.isArray() && sample.size() > 1 && sample.get(1).isNumber()) {
                    return Optional.of(sample.get(1).asInt());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * HRV (ms) de la última noche: hrvSummary.lastNightAvg; si no está, la última lectura
     * de hrvReadings.
     */
    public Optional<Double> hrvLastNight(JsonNode hrv) {
        if (hrv == null || hrv.isNull()) {
            return Optional.empty();
        }
        JsonNode avg = hrv.path("hrvSummary").path("lastNightAvg");
        if (avg.isNumber()) {
            return Optional.of(avg.asDouble());
        }
        JsonNode readings = hrv.path("hrvReadings");
        if (readings.isArray()) {
            for (int i = readings.size() - 1; i >= 0; i--) {
                JsonNode value = readings.get(i).path("hrvValue");
                if (value.isNumber()) {
                    return Optional.of(value.asDouble());
                }
            }
        }
        return Optional.empty();
    }

    /** Horas dormidas (sleepTimeSeconds / 3600, 1 decimal). */
    public Optional<Double> sleepHours(JsonNode dailySleep) {
        if (dailySleep == null || dailySleep.isNull()) {
            return Optional.empty();
        }
        JsonNode node = dailySleep.has("dailySleepDTO") ? dailySleep.get("dailySleepDTO") : dailySleep;
        JsonNode seconds = node.path("sleepTimeSeconds");
        if (!seconds.isNumber()) {
            return Optional.empty();
        }
        double hours = seconds.asDouble() / 3600.0;
        return Optional.of(Math.round(hours * 10.0) / 10.0);
    }

    private static String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String clean = body.replaceAll("\\s+", " ").trim();
        return ": " + (clean.length() > 200 ? clean.substring(0, 200) + "..." : clean);
    }

    private static boolean isAuthStatus(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private static String firstText(JsonNode json, String... fields) {
        for (String f : fields) {
            if (json.hasNonNull(f)) {
                return json.get(f).asText();
            }
        }
        return null;
    }
}
