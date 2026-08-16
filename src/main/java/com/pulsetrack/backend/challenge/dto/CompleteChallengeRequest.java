package com.pulsetrack.backend.challenge.dto;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Reglage d'un defi, de l'une des deux facons.
 *
 * <p><strong>Avec {@code workoutId}</strong>, ce qui est recommande : les
 * chiffres sont ceux de la seance enregistree, calcules par le serveur, et le
 * defi reste rattache a la sortie qui l'a joue.
 *
 * <p><strong>Avec {@code distanceMeters} et {@code durationSeconds}</strong>
 * quand la seance n'est pas enregistree — tapis de course, oubli de demarrage.
 * On croit alors l'utilisateur sur parole.
 *
 * <p>L'application mobile n'a normalement besoin ni de l'un ni de l'autre :
 * passer {@code challengeId} a {@code POST /api/v1/workouts} regle le defi dans
 * le meme appel, ce qui compte quand le reseau revient a peine.
 */
public record CompleteChallengeRequest(
        UUID workoutId,
        @DecimalMin("0.0") Double distanceMeters,
        @Min(1) Long durationSeconds) {

    /** Vrai quand la seance enregistree fait foi. */
    public boolean referencesWorkout() {
        return workoutId != null;
    }

    /** Vrai quand les chiffres sont declares a la main. */
    public boolean isDeclared() {
        return distanceMeters != null && durationSeconds != null;
    }
}
