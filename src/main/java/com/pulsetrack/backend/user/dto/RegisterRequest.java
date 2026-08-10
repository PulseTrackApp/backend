package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creation de compte. Le profil sportif est renseigne dans un second temps, via
 * {@code PUT /api/v1/me/profile} : l'onboarding mobile peut ainsi enchainer
 * inscription puis questionnaire sans bloquer l'un sur l'autre.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank
        @Size(min = 8, max = 100, message = "le mot de passe doit faire entre 8 et 100 caracteres")
        String password) {
}
