package com.pulsetrack.backend.common.error;

import java.time.Duration;
import java.util.Optional;

/**
 * Quota epuise, qu'il s'agisse de celui d'un service tiers ou d'un plafond que
 * nous imposons nous-memes. Traduit en HTTP 429.
 *
 * <p>Merite son propre code : le client doit comprendre qu'il faut attendre, et
 * non que sa requete etait mal formee.
 */
public class RateLimitedException extends RuntimeException {

    /** Nul quand le delai d'attente n'est pas connu, cas d'un quota tiers. */
    private final Duration retryAfter;

    public RateLimitedException(String message) {
        this(message, null);
    }

    /**
     * @param retryAfter delai avant nouvelle tentative, repercute dans l'en-tete
     *                   {@code Retry-After} : sans lui, un client bien eleve n'a
     *                   d'autre choix que de retenter au hasard
     */
    public RateLimitedException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
