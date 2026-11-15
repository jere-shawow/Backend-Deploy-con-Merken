package com.astronaut.backend.notifications.service;

import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.AlertState;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Regla de cuándo notificar (lógica pura, sin base de datos):
 *  - apenas un valor llega a muy alto / muy bajo  -> NOTIFY
 *  - si pasa de muy bajo a muy alto (o al revés)   -> NOTIFY
 *  - si sigue crítico y ya pasó el intervalo       -> NOTIFY (15 min pulso, 30 azúcar y temperatura, 1 día sueño)
 *  - si vuelve a un nivel no crítico               -> CLEAR (la próxima vez avisa de inmediato)
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
