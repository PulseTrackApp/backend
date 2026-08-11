package com.pulsetrack.backend.common.error;

/**
 * Code de reinitialisation inconnu, expire ou deja consomme. Traduit en HTTP 400.
 *
 * <p>Un seul type pour les trois cas, et un seul message cote client : les
 * distinguer apprendrait a un attaquant lesquels de ses essais ont existe.
 */
public class InvalidResetCodeException extends RuntimeException {

    public InvalidResetCodeException(String message) {
        super(message);
    }
}
