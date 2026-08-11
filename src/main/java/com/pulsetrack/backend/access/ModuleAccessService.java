package com.pulsetrack.backend.access;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source unique de verite sur ce qu'un compte a le droit d'utiliser.
 *
 * <p>Les droits ne voyagent pas dans le jeton d'acces. Celui-ci vit vingt-quatre
 * heures : y graver les modules ferait attendre une journee entiere avant qu'un
 * retrait prenne effet, ce qui viderait l'ecran d'administration de son sens. Ils
 * sont donc relus cote serveur, avec un cache de courte duree pour ne pas payer
 * une requete a chaque appel.
 */
@Service
public class ModuleAccessService {

    /**
     * Au-dela, on purge les entrees perimees avant d'en ajouter. Sans cette
     * borne, une longue serie de comptes differents ferait croitre la carte
     * indefiniment — c'est le meme garde-fou que dans le limiteur de debit.
     */
    private static final int MAX_TRACKED_USERS = 10_000;

    private final UserModuleRepository userModules;
    private final AccessProperties properties;
    private final Clock clock;
    private final ConcurrentMap<UUID, CachedGrants> cache = new ConcurrentHashMap<>();

    /**
     * {@code @Autowired} explicite : la classe expose deux constructeurs, et
     * sans cette marque Spring refuse de choisir et cherche un constructeur sans
     * argument qui n'existe pas.
     */
    @Autowired
    public ModuleAccessService(UserModuleRepository userModules, AccessProperties properties) {
        this(userModules, properties, Clock.systemUTC());
    }

    /** Reservee aux tests, pour piloter le temps et eprouver l'expiration du cache. */
    ModuleAccessService(UserModuleRepository userModules, AccessProperties properties, Clock clock) {
        this.userModules = userModules;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Modules effectivement utilisables par un compte.
     *
     * <p>L'immunite de l'administrateur est definie ici et nulle part ailleurs :
     * l'intercepteur et l'endpoint {@code /me/modules} doivent repondre la meme
     * chose, sinon l'application afficherait une rubrique que le serveur refuse,
     * ou l'inverse.
     *
     * @param admin vrai si le compte porte le role administrateur, auquel cas
     *              tout est ouvert sans consulter la base
     */
    public Set<AppModule> enabledFor(UUID userId, boolean admin) {
        if (admin) {
            return EnumSet.allOf(AppModule.class);
        }
        return grantedTo(userId);
    }

    public boolean isEnabled(UUID userId, boolean admin, AppModule module) {
        return enabledFor(userId, admin).contains(module);
    }

    /**
     * Remplace l'ensemble des droits d'un compte.
     *
     * <p>Remplacement complet et non ajout ou retrait unitaire : l'ecran
     * d'administration envoie l'etat de ses cases a cocher, et l'operation est
     * idempotente. Un appel rejoue ne peut donc pas produire un etat different
     * de celui qui est affiche.
     */
    @Transactional
    public Set<AppModule> replace(UUID userId, Set<AppModule> modules) {
        Set<AppModule> granted = toEnumSet(modules);
        userModules.deleteByUserId(userId);
        Instant now = clock.instant();
        List<UserModule> rows = granted.stream()
                .map(module -> new UserModule(userId, module, now))
                .toList();
        userModules.saveAll(rows);
        evict(userId);
        return granted;
    }

    /** Droits accordes a l'inscription. */
    @Transactional
    public void grantDefaults(UUID userId) {
        replace(userId, properties.defaultModules());
    }

    /**
     * Oublie les droits en cache pour ce compte, a chaque ecriture.
     *
     * <p>L'oubli a lieu avant la validation de la transaction : une lecture
     * concurrente glissee entre les deux remettrait en cache l'etat d'avant. Le
     * changement serait alors visible au bout du delai d'expiration au lieu de
     * l'etre immediatement — soit la garantie annoncee au client, « moins d'une
     * minute », et non une incoherence durable. Le cas est assez etroit et assez
     * benin pour ne pas justifier un accrochage a la validation.
     */
    public void evict(UUID userId) {
        cache.remove(userId);
    }

    private Set<AppModule> grantedTo(UUID userId) {
        Instant now = clock.instant();
        CachedGrants cached = cache.get(userId);
        if (cached != null && !cached.hasExpiredAt(now)) {
            return cached.modules();
        }
        Set<AppModule> snapshot = toEnumSet(userModules.findModulesByUserId(userId));
        if (cache.size() >= MAX_TRACKED_USERS) {
            purgeExpired(now);
        }
        cache.put(userId, new CachedGrants(snapshot, now.plus(properties.cacheTtl())));
        return snapshot;
    }

    private void purgeExpired(Instant now) {
        cache.values().removeIf(entry -> entry.hasExpiredAt(now));
    }

    /**
     * {@link EnumSet#copyOf(java.util.Collection)} refuse une collection vide,
     * faute de pouvoir en deduire le type de l'enumeration. Un compte prive de
     * tous ses modules — cas parfaitement legitime — leverait donc une exception
     * au lieu de rendre un ensemble vide.
     */
    private static Set<AppModule> toEnumSet(Set<AppModule> modules) {
        return modules.isEmpty() ? EnumSet.noneOf(AppModule.class) : EnumSet.copyOf(modules);
    }

    private record CachedGrants(Set<AppModule> modules, Instant expiresAt) {

        boolean hasExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
