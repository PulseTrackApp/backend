package com.pulsetrack.backend.access;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reglages du controle d'acces par module.
 *
 * @param adminEmail     compte promu administrateur au demarrage, et a
 *                       l'inscription s'il n'existe pas encore. Sans lui,
 *                       personne ne peut appeler {@code /api/v1/admin/**} et
 *                       l'application d'administration est inutilisable des son
 *                       premier ecran. Laisse vide, aucune promotion n'a lieu.
 * @param defaultModules modules accordes a toute nouvelle inscription. Tous par
 *                       defaut : le verrouillage est une restriction posee
 *                       sciemment, pas un parcours d'activation impose.
 * @param cacheTtl       duree de vie du cache des droits. Compromis entre une
 *                       requete par appel authentifie et le delai avant qu'un
 *                       retrait de droit prenne effet.
 */
@ConfigurationProperties(prefix = "pulsetrack.access")
@Validated
public record AccessProperties(String adminEmail,
                               @NotNull Set<AppModule> defaultModules,
                               @NotNull Duration cacheTtl) {

    /**
     * @return {@code true} si un compte administrateur est configure. Une chaine
     *         vide compte pour absent : c'est ce que produit une variable
     *         d'environnement declaree dans Coolify mais laissee sans valeur.
     */
    public boolean hasAdminEmail() {
        return adminEmail != null && !adminEmail.isBlank();
    }
}
