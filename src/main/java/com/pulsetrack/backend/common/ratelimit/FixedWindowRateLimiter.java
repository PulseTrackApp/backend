package com.pulsetrack.backend.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Compteur de tentatives par fenetre fixe, en memoire.
 *
 * <p>La premiere tentative ouvre une fenetre de la duree demandee ; les
 * suivantes s'y accumulent jusqu'a la limite, puis sont refusees jusqu'a
 * l'expiration de la fenetre. C'est plus grossier qu'une fenetre glissante — un
 * attaquant peut concentrer deux fois la limite a cheval sur deux fenetres —
 * mais cela suffit largement a rendre un bruteforce inoperant, sans stocker
 * l'horodatage de chaque tentative.
 *
 * <p><strong>Portee volontairement locale a l'instance.</strong> Le deploiement
 * vise (un conteneur derriere le proxy de Coolify) est mono-instance : un
 * compteur en memoire y est exact et evite d'imposer un Redis. Le jour ou
 * l'application est repliquee, cette classe devra etre remplacee par un
 * compteur partage, sans quoi la limite reelle sera multipliee par le nombre
 * d'instances.
 */
public class FixedWindowRateLimiter {

    /**
     * Au-dela de ce nombre de cles suivies, les fenetres expirees sont purgees.
     * Sans ce garde-fou, une avalanche d'adresses IP distinctes ferait grossir
     * la table indefiniment : le limiteur cense proteger l'application
     * deviendrait lui-meme le moyen de la faire tomber.
     */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Enregistre une tentative pour {@code key} et dit si elle est recevable.
     *
     * @param key         identifiant du compteur ; les appelants prefixent par
     *                    usage pour que deux limites ne partagent pas de seau
     * @param maxAttempts nombre de tentatives tolerees par fenetre
     * @param window      duree de la fenetre
     * @return {@link Optional#empty()} si la tentative est acceptee, sinon le
     *         delai restant avant la reouverture de la fenetre
     */
    public Optional<Duration> tryConsume(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();
        if (windows.size() >= MAX_TRACKED_KEYS) {
            purgeExpired(now);
        }

        // `compute` est atomique par cle : deux requetes concurrentes sur le
        // meme compte ne peuvent pas lire puis ecrire le meme compteur.
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.hasExpiredAt(now)
                        ? new Window(now.plus(window), 1)
                        : existing.incremented());

        if (current.attempts() <= maxAttempts) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.expiresAt()));
    }

    /**
     * Oublie le compteur d'une cle. Sert a ne pas penaliser un utilisateur
     * legitime apres une authentification reussie.
     */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Nombre de cles en memoire. Expose pour verifier la purge en test. */
    int trackedKeys() {
        return windows.size();
    }

    private void purgeExpired(Instant now) {
        windows.values().removeIf(window -> window.hasExpiredAt(now));
    }

    /**
     * @param expiresAt instant de reouverture
     * @param attempts  tentatives comptees depuis l'ouverture, refusees comprises
     */
    private record Window(Instant expiresAt, int attempts) {

        boolean hasExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }

        Window incremented() {
            return new Window(expiresAt, attempts + 1);
        }
    }
}
