package com.pulsetrack.backend.user.dto;

import java.util.UUID;

/**
 * Reponse d'inscription, de connexion et de renouvellement.
 *
 * @param accessToken             jeton a placer dans l'en-tete {@code Authorization: Bearer ...}
 * @param tokenType               toujours {@code Bearer}
 * @param expiresInSeconds        duree de validite restante du jeton d'acces,
 *                                pour que le client anticipe le renouvellement
 *                                sans decoder le jeton
 * @param refreshToken            jeton opaque a conserver pour obtenir un
 *                                nouveau jeton d'acces via {@code POST /api/v1/auth/refresh},
 *                                sans redemander le mot de passe. Il change a
 *                                chaque renouvellement : le client doit
 *                                remplacer celui qu'il detient.
 * @param refreshExpiresInSeconds duree de validite du jeton de renouvellement ;
 *                                passe ce delai, il faut se reconnecter
 * @param userId                  identifiant du compte
 * @param email                   email normalise
 * @param profileCompleted        {@code false} tant que le profil sportif n'est pas
 *                                renseigne : le client mobile route alors vers l'onboarding
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds,
        UUID userId,
        String email,
        boolean profileCompleted) {
}
