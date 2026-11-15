package com.astronaut.backend.reports.config;

import com.astronaut.backend.reports.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Medición cada 10 minutos y limpieza horaria de informes vencidos. */
@Component
@ConditionalOnProperty(name = "reports.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportService service;

    public ReportScheduler(ReportService service) {
        this.service = service;
    }

    @Scheduled(fixedRateString = "${reports.snapshot-interval-minutes:10}", initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void snapshot() {
        try {
            service.captureSnapshot();
        } catch (RuntimeException e) {
            log.error("No se pudo agregar la medición al informe diario: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60, initialDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void purge() {
        try {
            service.purgeExpired();
        } catch (RuntimeException e) {
            log.error("No se pudieron borrar los informes vencidos: {}", e.getMessage());
        }
    }
}
