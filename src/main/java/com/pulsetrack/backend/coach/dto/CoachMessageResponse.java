package com.pulsetrack.backend.coach.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.coach.CoachMessageKind;

/**
 * Conseil rendu au client.
 *
 * <p>Le prompt envoye au modele n'est pas expose : il contient l'integralite des
 * donnees de sante de l'utilisateur, deja disponibles ailleurs dans l'API, et il
 * n'a aucune utilite a l'ecran.
 *
 * @param fromCache le conseil existait deja ; aucun appel a Gemini n'a eu lieu,
 *                  donc aucun quota consomme
 */
public record CoachMessageResponse(
        UUID id,
        CoachMessageKind kind,
        LocalDate weekStart,
        String content,
        String model,
        Instant createdAt,
        boolean fromCache) {
}
