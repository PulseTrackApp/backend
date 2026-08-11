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
 * @param adminPassword  mot de passe d'amorçage. Renseigne, le compte
 *                       administrateur est cree au demarrage s'il n'existe pas
 *                       encore, ce qui evite d'avoir a s'inscrire avant de
 *                       pouvoir ouvrir l'application d'administration.
 *                       <p>Il ne sert qu'a la creation : un compte deja present
 *                       n'est jamais reecrit, sinon chaque redeploiement
 *                       ecraserait un mot de passe change depuis l'application.
 *                       <p>A retirer une fois la premiere connexion faite : une
 *                       valeur posee la se lit en clair dans le tableau de bord
 *                       de la plateforme et survit dans l'historique de
 *                       configuration.
 * @param defaultModules socle ouvert a toute nouvelle inscription. Les modules
 *                       absents de cette liste ne s'obtiennent que par un geste
 *                       de l'administrateur — c'est la le levier du produit.
 * @param cacheTtl       duree de vie du cache des droits. Compromis entre une
 *                       requete par appel authentifie et le delai avant qu'un
 *                       retrait de droit prenne effet.
 */
@ConfigurationProperties(prefix = "pulsetrack.access")
@Validated
public record AccessProperties(String adminEmail,
                               String adminPassword,
                               @NotNull Set<AppModule> defaultModules,
                               @NotNull Duration cacheTtl) {

    /**
     * Longueur minimale, alignee sur celle exigee a l'inscription. Un compte
     * d'administration ne doit pas etre plus facile a deviner que celui d'un
     * utilisateur ordinaire.
     */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * @return {@code true} si un compte administrateur est configure. Une chaine
     *         vide compte pour absent : c'est ce que produit une variable
     *         d'environnement declaree dans Coolify mais laissee sans valeur.
     */
    public boolean hasAdminEmail() {
        return adminEmail != null && !adminEmail.isBlank();
    }

    public boolean hasAdminPassword() {
        return adminPassword != null && !adminPassword.isBlank();
    }

    public boolean adminPasswordIsLongEnough() {
        return hasAdminPassword() && adminPassword.length() >= MIN_PASSWORD_LENGTH;
    }
}
