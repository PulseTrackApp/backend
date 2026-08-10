package com.pulsetrack.backend.user.dto;

import java.util.UUID;

/**
 * Reponse d'inscription et de connexion.
 *
 * @param accessToken       jeton a placer dans l'en-tete {@code Authorization: Bearer ...}
 * @param tokenType         toujours {@code Bearer}
 * @param expiresInSeconds  duree de validite restante, pour que le client
 *                          anticipe le renouvellement sans decoder le jeton
 * @param userId            identifiant du compte
 * @param email             email normalise
 * @param profileCompleted  {@code false} tant que le profil sportif n'est pas
 *                          renseigne : le client mobile route alors vers l'onboarding
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        boolean profileCompleted) {
}
