package com.pulsetrack.backend.common.error;

/**
 * Code de confirmation d'adresse inconnu, expire ou deja consomme. Traduit en
 * HTTP 400.
 *
 * <p>Un seul type pour les trois cas, et un seul message cote client : les
 * distinguer apprendrait a un attaquant lesquels de ses essais ont existe.
 */
public class InvalidVerificationCodeException extends RuntimeException {

    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}
