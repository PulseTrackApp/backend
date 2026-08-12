package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande d'un nouveau code de confirmation.
 *
 * @param email adresse du compte ; la reponse est la meme qu'elle existe, soit
 *              deja verifiee, soit inconnue
 */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 320) String email) {
}
