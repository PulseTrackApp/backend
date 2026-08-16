package com.pulsetrack.backend.challenge.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Point d'etape demande en cours d'effort.
 *
 * <p>Facultatif : le plan remis au depart suffit a jouer toutes les alertes hors
 * ligne. Cet appel sert a un ecran de suivi qui veut l'avis du serveur, pas au
 * declenchement des alertes.
 *
 * <p><strong>Il n'ecrit rien</strong> : il ne change pas l'etat du defi, ne
 * consomme rien, et ne peut pas le faire echouer.
 */
public record ChallengeProgressRequest(
        @Min(0) long elapsedSeconds,
        @DecimalMin("0.0") double distanceMeters) {
}
