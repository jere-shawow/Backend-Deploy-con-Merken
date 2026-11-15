package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.AlertState;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Regla de cuándo notificar (lógica pura, sin base de datos):
 *  - apenas un valor sale del rango normal (bajo, alto, muy bajo, muy alto) -> NOTIFY
 *  - si cambia de nivel (p. ej. alto -> muy alto, o muy bajo -> muy alto)    -> NOTIFY de inmediato
 *  - si sigue en el mismo nivel y ya pasó el intervalo                       -> NOTIFY
 *  - si vuelve al rango normal                                               -> CLEAR (la próxima vez avisa de inmediato)
 */
public final class AlertPolicy {

    public enum Decision { NOTIFY, WAIT, CLEAR, NONE }

    private AlertPolicy() {
    }

    public static Decision decide(Optional<AlertState> state, Optional<AlertLevel> current,
                                  Instant now, Duration repeatEvery) {
        if (current.isEmpty()) {
            return state.isPresent() ? Decision.CLEAR : Decision.NONE;
        }
        if (state.isEmpty()) {
            return Decision.NOTIFY;
        }
        AlertState s = state.get();
        if (s.getLevel() != current.get()) {
            return Decision.NOTIFY;
        }
        Instant next = s.getLastNotifiedAt().plus(repeatEvery);
        return now.isBefore(next) ? Decision.WAIT : Decision.NOTIFY;
    }
}
