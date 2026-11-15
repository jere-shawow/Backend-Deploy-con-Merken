package com.astronaut.backend.service;

import com.astronaut.backend.dto.GarminLinkStatus;
import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.garmin.GarminApiClient;
import com.astronaut.backend.garmin.GarminApiClient.AccessToken;
import com.astronaut.backend.garmin.GarminException;
import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.GarminLink;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Vincula astronautas con Garmin y sincroniza HeartRate, SleepHours, BodyBattery y HRV
 * (todas con el mismo token JWT de la Personal API).
 *
 * {@link #syncAll()} lo ejecuta el planificador cada N segundos
 * (N = refreshIntervalSeconds de las settings globales).
 */
@Service
public class GarminSyncService {

    private static final Logger log = LoggerFactory.getLogger(GarminSyncService.class);

    private final GarminLinkRepository linkRepository;
    private final AstronautRepository astronautRepository;
    private final GarminApiClient garmin;
    private final Duration sleepRefresh;
    private final Duration bodyBatteryRefresh;

    /** Tokens de acceso en memoria, por astronauta (válidos ~24 h). */
    private final Map<Long, AccessToken> tokens = new ConcurrentHashMap<>();
    /** Última consulta de sueño, por astronauta (el sueño cambia una vez al día). */
    private final Map<Long, Instant> lastSleepFetch = new ConcurrentHashMap<>();
    /** Última consulta de Body Battery, por astronauta (como máximo 1 vez por minuto). */
    private final Map<Long, Instant> lastBodyBatteryFetch = new ConcurrentHashMap<>();

    public GarminSyncService(GarminLinkRepository linkRepository,
                             AstronautRepository astronautRepository,
                             GarminApiClient garmin,
                             @Value("${garmin.sleep-refresh-minutes:10}") long sleepRefreshMinutes,
                             @Value("${garmin.body-battery-refresh-seconds:60}") long bodyBatteryRefreshSeconds) {
        this.linkRepository = linkRepository;
        this.astronautRepository = astronautRepository;
        this.garmin = garmin;
        this.sleepRefresh = Duration.ofMinutes(sleepRefreshMinutes);
        this.bodyBatteryRefresh = Duration.ofSeconds(bodyBatteryRefreshSeconds);
    }

    // ---------------------------------------------------------- vínculos

    @Transactional(readOnly = true)
    public GarminLinkStatus status(Long astronautId) {
        requireAstronaut(astronautId);
        return linkRepository.findByAstronautId(astronautId)
                .map(GarminLinkStatus::of)
                .orElse(GarminLinkStatus.notLinked(astronautId));
    }

    /**
     * Vincula (o reemplaza la API key de) un astronauta. Valida la key pidiendo
     * un token a Garmin y hace una primera sincronización inmediata.
     */
    @Transactional
    public GarminLinkStatus link(Long astronautId, String apiKey) {
        Astronaut astronaut = requireAstronaut(astronautId);

        // Lanza GarminException si la key no sirve -> el vínculo no se guarda.
        AccessToken token = garmin.obtainToken(apiKey);

        GarminLink link = linkRepository.findByAstronautId(astronautId)
                .orElseGet(() -> new GarminLink(astronautId, apiKey));
        link.setApiKey(apiKey.trim());
        link.setLinkedAt(Instant.now());
        link.setLastError(null);
        link = linkRepository.save(link);

        astronaut.setGarminLinked(true);
        astronautRepository.save(astronaut);

        tokens.put(astronautId, token);
        clearCaches(astronautId);
        syncOne(link);
        return GarminLinkStatus.of(link);
    }

    @Transactional
    public void unlink(Long astronautId) {
        Astronaut astronaut = requireAstronaut(astronautId);
        linkRepository.deleteByAstronautId(astronautId);
        astronaut.setGarminLinked(false);
        astronautRepository.save(astronaut);
        tokens.remove(astronautId);
        clearCaches(astronautId);
    }

    /** Sincronización manual de un astronauta. */
    @Transactional
    public GarminLinkStatus syncNow(Long astronautId) {
        requireAstronaut(astronautId);
        GarminLink link = linkRepository.findByAstronautId(astronautId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El astronauta " + astronautId + " no tiene un dispositivo Garmin vinculado"));
        clearCaches(astronautId);
        syncOne(link);
        return GarminLinkStatus.of(link);
    }

    // ------------------------------------------------------ sincronización

    /** Ejecutado por el planificador. Un error en un astronauta no detiene a los demás. */
    @Transactional
    public void syncAll() {
        for (GarminLink link : linkRepository.findAll()) {
            syncOne(link);
        }
    }

    /**
     * Obtiene HeartRate (siempre) y SleepHours (cada {@code garmin.sleep-refresh-minutes})
     * y actualiza al astronauta. Los errores quedan registrados en el vínculo.
     */
    synchronized void syncOne(GarminLink link) {
        Long astronautId = link.getAstronautId();
        try {
            Optional<Astronaut> maybeAstronaut = astronautRepository.findById(astronautId);
            if (maybeAstronaut.isEmpty()) {
                linkRepository.delete(link);
                return;
            }
            Astronaut astronaut = maybeAstronaut.get();
            LocalDate today = garmin.today();
            boolean changed = false;

            Optional<Integer> heartRate = garmin.latestHeartRate(
                    withToken(link, token -> garmin.getDailyHeartRate(token, today)));
            if (heartRate.isPresent() && heartRate.get() >= 20 && heartRate.get() <= 250) {
                astronaut.setHeartRate(heartRate.get());
                changed = true;
            }

            // Body Battery cambia durante el día: como máximo 1 consulta por minuto
            Instant lastBb = lastBodyBatteryFetch.get(astronautId);
            if (lastBb == null || Instant.now().isAfter(lastBb.plus(bodyBatteryRefresh))) {
                Optional<Integer> bodyBattery = garmin.latestBodyBattery(
                        withToken(link, token -> garmin.getDailyBodyBattery(token, today)));
                lastBodyBatteryFetch.put(astronautId, Instant.now());
                if (bodyBattery.isPresent() && bodyBattery.get() >= 0 && bodyBattery.get() <= 100) {
                    astronaut.setBodyBattery(bodyBattery.get());
                    changed = true;
                }
            }

            // Sueño y HRV se calculan una vez por noche: cada garmin.sleep-refresh-minutes
            Instant lastSleep = lastSleepFetch.get(astronautId);
            if (lastSleep == null || Instant.now().isAfter(lastSleep.plus(sleepRefresh))) {
                Optional<Double> sleep = garmin.sleepHours(
                        withToken(link, token -> garmin.getDailySleep(token, today)));
                Optional<Double> hrv = garmin.hrvLastNight(
                        withToken(link, token -> garmin.getDailyHrv(token, today)));
                lastSleepFetch.put(astronautId, Instant.now());
                if (sleep.isPresent() && sleep.get() >= 0 && sleep.get() <= 24) {
                    astronaut.setSleepHours(sleep.get());
                    changed = true;
                }
                if (hrv.isPresent() && hrv.get() >= 0 && hrv.get() <= 300) {
                    astronaut.setHrv(hrv.get());
                    changed = true;
                }
            }

            Instant now = Instant.now();
            astronaut.setGarminLinked(true);
            astronaut.setGarminLastSync(now);
            astronautRepository.save(astronaut);

            link.setLastSyncAt(now);
            link.setLastError(changed ? null : "Garmin no tiene datos de hoy todavía (sincroniza el reloj con la app)");
            linkRepository.save(link);
        } catch (GarminException e) {
            log.warn("Sync Garmin falló para astronauta {}: {}", astronautId, e.getMessage());
            link.setLastError(e.getMessage());
            linkRepository.save(link);
        } catch (RuntimeException e) {
            log.error("Error inesperado sincronizando Garmin para astronauta {}", astronautId, e);
            link.setLastError("Error inesperado: " + e.getClass().getSimpleName());
            linkRepository.save(link);
        }
    }

    /** Ejecuta una llamada con token válido; si Garmin responde 401/403 renueva el token una vez. */
    private JsonNode withToken(GarminLink link, Function<AccessToken, JsonNode> call) {
        AccessToken token = currentToken(link);
        try {
            return call.apply(token);
        } catch (GarminException e) {
            if (!e.isAuthError()) {
                throw e;
            }
            tokens.remove(link.getAstronautId());
            return call.apply(currentToken(link));
        }
    }

    private AccessToken currentToken(GarminLink link) {
        AccessToken token = tokens.get(link.getAstronautId());
        if (token == null || token.isExpired()) {
            token = garmin.obtainToken(link.getApiKey());
            tokens.put(link.getAstronautId(), token);
        }
        return token;
    }

    private void clearCaches(Long astronautId) {
        lastSleepFetch.remove(astronautId);
        lastBodyBatteryFetch.remove(astronautId);
    }

    private Astronaut requireAstronaut(Long id) {
        return astronautRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un astronauta con id " + id));
    }
}
