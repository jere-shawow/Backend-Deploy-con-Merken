package com.astronaut.backend.dto;

import com.astronaut.backend.model.GarminLink;

import java.time.Instant;

/**
 * Estado del vínculo Garmin que se expone al frontend (sin la API key).
 */
public record GarminLinkStatus(
        Long astronautId,
        boolean linked,
        String apiKeyHint,
        Instant linkedAt,
        Instant lastSyncAt,
        String lastError) {

    public static GarminLinkStatus notLinked(Long astronautId) {
        return new GarminLinkStatus(astronautId, false, null, null, null, null);
    }

    public static GarminLinkStatus of(GarminLink link) {
        return new GarminLinkStatus(
                link.getAstronautId(),
                true,
                mask(link.getApiKey()),
                link.getLinkedAt(),
                link.getLastSyncAt(),
                link.getLastError());
    }

    private static String mask(String key) {
        if (key == null || key.length() < 8) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }
}
