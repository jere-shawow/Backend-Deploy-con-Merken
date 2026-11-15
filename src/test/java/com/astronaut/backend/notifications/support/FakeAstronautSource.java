package com.astronaut.backend.notifications.support;

import com.astronaut.backend.notifications.client.AstronautSnapshot;
import com.astronaut.backend.notifications.client.AstronautSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Astronautas que la prueba controla (reemplaza la lectura desde la base). */
public class FakeAstronautSource implements AstronautSource {

    private final List<AstronautSnapshot> astronauts = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    @Override
    public List<AstronautSnapshot> findAll() {
        if (down) {
            throw new IllegalStateException("base de datos no disponible");
        }
        return List.copyOf(astronauts);
    }

    /** Reemplaza (o agrega) un astronauta con estos valores. */
    public void put(long id, String name, Integer pulse, Double glucose, Double temperature, Double sleep) {
        astronauts.removeIf(a -> a.id() == id);
        astronauts.add(new AstronautSnapshot(id, name, pulse, glucose, temperature, sleep));
    }

    /** Valores normales en todo. */
    public void putNormal(long id, String name) {
        put(id, name, 72, 90.0, 36.6, 8.0);
    }

    public void remove(long id) { astronauts.removeIf(a -> a.id() == id); }
    public void clear() { astronauts.clear(); down = false; }
    public void down(boolean down) { this.down = down; }
}
