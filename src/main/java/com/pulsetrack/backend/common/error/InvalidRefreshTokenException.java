package com.pulsetrack.backend.common.error;

/**
 * Jeton de renouvellement inconnu, expire ou revoque. Traduit en HTTP 401.
 *
 * <p>Distinct de {@code BadCredentialsException} : le client mobile doit
 * pouvoir separer « ce mot de passe est faux » de « cette session est finie »,
 * le second devant le ramener a l'ecran de connexion sans afficher d'erreur de
 * saisie.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
