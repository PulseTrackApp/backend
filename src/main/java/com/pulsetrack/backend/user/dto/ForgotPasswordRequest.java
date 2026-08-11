package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande d'un code de reinitialisation.
 *
 * @param email adresse du compte ; la reponse est la meme qu'elle existe ou non
 */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
}
