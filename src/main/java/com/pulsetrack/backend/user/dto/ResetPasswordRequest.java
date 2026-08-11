package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Choix d'un nouveau mot de passe, muni du code recu par courriel.
 *
 * @param code        code a huit caracteres ; la casse et les espaces sont
 *                    tolerés, le serveur normalise avant comparaison
 * @param newPassword memes bornes qu'a l'inscription : une reinitialisation ne
 *                    doit pas etre une porte derobee vers un mot de passe plus
 *                    faible que ceux acceptes a la creation du compte
 */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank
        @Size(min = 8, max = 100, message = "le mot de passe doit faire entre 8 et 100 caracteres")
        String newPassword) {
}
