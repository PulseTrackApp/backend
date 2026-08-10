package com.pulsetrack.backend.common.error;

/**
 * Quota d'un service tiers epuise. Traduit en HTTP 429.
 *
 * <p>Merite son propre code : le client doit comprendre qu'il faut attendre, et
 * non que sa requete etait mal formee.
 */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
