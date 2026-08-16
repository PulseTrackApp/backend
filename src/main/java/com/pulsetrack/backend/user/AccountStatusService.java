package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dit si un compte est suspendu, sans interroger la base a chaque requete.
 *
 * <p><strong>Pourquoi ce service existe.</strong> Une suspension doit prendre
 * effet tout de suite. Or le jeton d'acces vit vingt-quatre heures : sans
 * verification a chaque requete, un compte ferme continuerait de fonctionner
 * jusqu'a l'expiration de son jeton — ce qui viderait la suspension de son sens
 * exactement dans le cas ou l'on s'en sert.
 *
 * <p>Le cache est court (trente secondes) et vide a l'ecriture. Il ne sert qu'a
 * eviter une lecture par requete HTTP ; meme perime, il ne peut retarder une
 * suspension que d'une demi-minute — et l'administration l'oublie explicitement
 * en fermant un compte, si bien que le cas ne se produit qu'entre deux
 * instances, s'il y en avait plusieurs un jour.
 *
 * <p>Meme forme que {@code ModuleAccessService}, y compris son garde-fou sur la
 * taille : un cache sans borne finit par tenir tous les comptes en memoire.
 */
@Service
public class AccountStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    /** Au-dela, on purge les entrees perimees avant d'en ajouter une. */
    private static final int MAX_TRACKED_USERS = 5_000;

    private final UserRepository users;
    private final ConcurrentMap<UUID, CachedStatus> cache = new ConcurrentHashMap<>();

    public AccountStatusService(UserRepository users) {
        this.users = users;
    }

    /**
     * @return l'etat du compte ; {@link Status#active()} si le compte n'existe
     *         pas — ce n'est pas a ce service de trancher un compte fantome, la
     *         chaine de securite et les controleurs s'en chargent avec un
     *         message juste
     */
    @Transactional(readOnly = true)
    public Status of(UUID userId) {
        Instant now = Instant.now();
        CachedStatus cached = cache.get(userId);
        if (cached != null && !cached.hasExpiredAt(now)) {
            return cached.status();
        }

        Status status = users.findById(userId)
                .map(user -> new Status(user.isDisabled(), user.getDisabledReason()))
                .orElseGet(Status::active);

        if (cache.size() >= MAX_TRACKED_USERS) {
            cache.values().removeIf(entry -> entry.hasExpiredAt(now));
        }
        cache.put(userId, new CachedStatus(status, now.plus(CACHE_TTL)));
        return status;
    }

    /**
     * Oublie l'etat en cache, a chaque suspension ou reouverture.
     *
     * <p>A appeler <strong>apres</strong> l'ecriture : une lecture concurrente
     * glissee entre les deux remettrait en cache l'etat d'avant, et la
     * suspension paraitrait n'avoir servi a rien.
     */
    public void forget(UUID userId) {
        cache.remove(userId);
    }

    /**
     * Etat d'un compte.
     *
     * @param reason raison saisie par l'administrateur ; peut etre nulle meme
     *               quand le compte est suspendu, si personne n'en a donne
     */
    public record Status(boolean disabled, String reason) {

        public static Status active() {
            return new Status(false, null);
        }

        /** Message a rendre a l'utilisateur, raison comprise quand il y en a une. */
        public String message() {
            return reason == null || reason.isBlank()
                    ? "Ce compte a été suspendu."
                    : "Ce compte a été suspendu : " + reason;
        }
    }

    private record CachedStatus(Status status, Instant expiresAt) {

        boolean hasExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
