package com.astronaut.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Variables globales de la aplicación (una sola fila, id = 1).
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

    public static final long SINGLETON_ID = 1L;
    public static final int DEFAULT_REFRESH_SECONDS = 5;
    public static final int DEFAULT_REPORT_RETENTION_DAYS = 7;

    @Id
    private Long id = SINGLETON_ID;

    /** Cada cuántos segundos se sincroniza Garmin y se refresca el dashboard. */
    @NotNull(message = "El intervalo de refresco es obligatorio")
    @Min(value = 5, message = "El intervalo mínimo es 5 segundos")
    @Max(value = 3600, message = "El intervalo máximo es 3600 segundos")
    @Column(name = "refresh_interval_seconds", nullable = false)
    private Integer refreshIntervalSeconds = DEFAULT_REFRESH_SECONDS;

    /** Días que se conservan los informes diarios antes de borrarse (vacío = 7). */
    @Min(value = 1, message = "Los informes deben conservarse al menos 1 día")
    @Max(value = 365, message = "Los informes se pueden conservar como máximo 365 días")
    @Column(name = "report_retention_days")
    private Integer reportRetentionDays = DEFAULT_REPORT_RETENTION_DAYS;

    public AppSettings() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) { this.refreshIntervalSeconds = refreshIntervalSeconds; }

    public Integer getReportRetentionDays() { return reportRetentionDays; }
    public void setReportRetentionDays(Integer reportRetentionDays) { this.reportRetentionDays = reportRetentionDays; }

    /** Retención efectiva: si no se ingresó un valor, 7 días. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public int effectiveReportRetentionDays() {
        return reportRetentionDays == null ? DEFAULT_REPORT_RETENTION_DAYS : reportRetentionDays;
    }
}
