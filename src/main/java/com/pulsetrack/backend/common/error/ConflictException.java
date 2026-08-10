package com.pulsetrack.backend.common.error;

/**
 * Etat courant de la ressource incompatible avec l'operation demandee
 * (ex. inscription avec un email deja pris). Traduit en HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
