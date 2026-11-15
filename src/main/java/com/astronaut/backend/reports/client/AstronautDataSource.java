package com.astronaut.backend.reports.client;

import com.astronaut.backend.reports.model.AstronautData;

import java.util.List;
import java.util.Optional;

/** Datos de los astronautas y de la configuración que necesita el informe. */
public interface AstronautDataSource {

    List<AstronautData> findAll();

    /** Días de retención configurados en la app (engranaje). */
    Optional<Integer> reportRetentionDays();
}
