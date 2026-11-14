package com.astronaut.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Vínculo entre un astronauta y su cuenta de Garmin Connect (Personal API).
 *
 * La API key nunca se devuelve por la API REST: solo se usa internamente
 * para obtener tokens de acceso.
 */
@Entity
@Table(name = "garmin_links")
public class GarminLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "astronaut_id", nullable = false, unique = true)
    private Long astronautId;

    @Column(name = "api_key", nullable = false, length = 2000)
    private String apiKey;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public GarminLink() {
    }

    public GarminLink(Long astronautId, String apiKey) {
        this.astronautId = astronautId;
        this.apiKey = apiKey;
        this.linkedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAstronautId() { return astronautId; }
    public void setAstronautId(Long astronautId) { this.astronautId = astronautId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
