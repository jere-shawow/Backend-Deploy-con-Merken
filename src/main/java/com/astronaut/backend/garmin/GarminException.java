package com.astronaut.backend.garmin;

/**
 * Error al comunicarse con Garmin Connect.
 * {@code authError = true} indica API key/token inválido o expirado.
 */
public class GarminException extends RuntimeException {

    private final boolean authError;

    public GarminException(String message, boolean authError) {
        super(message);
        this.authError = authError;
    }

    public GarminException(String message, boolean authError, Throwable cause) {
        super(message, cause);
        this.authError = authError;
    }

    public boolean isAuthError() {
        return authError;
    }
}
