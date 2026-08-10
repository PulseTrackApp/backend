package com.pulsetrack.backend.summary.dto;

import java.util.UUID;

import com.pulsetrack.backend.goal.GoalType;

/**
 * Etat d'avancement d'un objectif.
 *
 * @param currentValue     realise a ce jour, dans l'unite de l'objectif ;
 *                         {@code null} si non mesurable faute de donnees
 * @param completionPercent pourcentage d'accomplissement, non plafonne a 100
 * @param remaining        reste a faire ; 0 une fois l'objectif atteint
 */
public record GoalProgressResponse(
        UUID goalId,
        GoalType type,
        String unit,
        double targetValue,
        Double currentValue,
        Double completionPercent,
        Double remaining,
        boolean achieved) {
}
