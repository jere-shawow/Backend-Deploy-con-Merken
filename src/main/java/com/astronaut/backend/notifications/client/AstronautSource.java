package com.astronaut.backend.notifications.client;

import java.util.List;

/**
 * De dónde obtiene el monitor los datos de los astronautas.
 * En el monolito se leen directo de la base (ver {@link RepositoryAstronautSource});
 * las pruebas usan una implementación en memoria.
 */
public interface AstronautSource {

    List<AstronautSnapshot> findAll();
}
