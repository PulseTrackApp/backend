package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Renouvellement d'une session, et deconnexion.
 *
 * @param refreshToken derniere valeur recue ; les precedentes ont ete
 *                     consommees par la rotation et sont refusees
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
