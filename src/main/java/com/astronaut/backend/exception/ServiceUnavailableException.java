package com.astronaut.backend.exception;

/** No se pudo leer un dato necesario (p. ej. la base de datos no responde). Responde 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
