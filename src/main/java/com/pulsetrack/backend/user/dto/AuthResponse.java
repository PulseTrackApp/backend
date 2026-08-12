package com.pulsetrack.backend.user.dto;

import java.util.UUID;

import com.pulsetrack.backend.user.Role;

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
 * @param emailVerified           {@code false} tant que l'adresse n'a pas ete
 *                                confirmee par le code envoye a l'inscription.
 *                                Le client peut inviter a la confirmer ; c'est
 *                                le serveur qui decide si elle est exigee, selon
 *                                {@code pulsetrack.security.email-verification.required}
 * @param role                    niveau de privilege du compte. Presente ici bien
 *                                qu'il figure deja dans le jeton d'acces : sans
 *                                cela, l'application d'administration devrait
 *                                decoder elle-meme le JWT pour savoir si elle a
 *                                affaire a un administrateur, et refuser
 *                                proprement une connexion legitime mais sans
 *                                privilege. Le serveur reste seul juge des
 *                                acces ; ce champ ne sert qu'a orienter
 *                                l'interface.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds,
        UUID userId,
        String email,
        boolean profileCompleted,
        boolean emailVerified,
        Role role) {
}
