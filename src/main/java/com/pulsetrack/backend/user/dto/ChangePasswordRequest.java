package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changement de mot de passe par quelqu'un qui connait l'ancien.
 *
 * @param currentPassword mot de passe actuel, exige meme si l'appelant presente
 *                        un jeton valide : un telephone laisse deverrouille ne
 *                        doit pas suffire a s'approprier le compte
 * @param newPassword     memes bornes qu'a l'inscription
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank
        @Size(min = 8, max = 100, message = "le mot de passe doit faire entre 8 et 100 caracteres")
        String newPassword) {
}
