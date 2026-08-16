package com.pulsetrack.backend.summary.dto;

import java.util.UUID;

import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.motivation.Appreciation;

/**
 * Etat d'avancement d'un objectif, et ce qu'il faut en penser.
 *
 * @param currentValue      realise a ce jour, dans l'unite de l'objectif ;
 *                          {@code null} si non mesurable faute de donnees
 * @param completionPercent pourcentage d'accomplissement, non plafonne a 100
 * @param remaining         reste a faire ; 0 une fois l'objectif atteint
 * @param elapsedPercent    part de la semaine deja ecoulee, dans le fuseau
 *                          demande. <strong>C'est la reference honnete</strong> :
 *                          40 % d'un objectif le mardi est en avance, le samedi
 *                          c'est en retard. {@code null} pour un objectif de
 *                          poids, qui n'est pas hebdomadaire
 * @param onTrack           {@code completionPercent} au moins egal a
 *                          {@code elapsedPercent}, avec cinq points de tolerance
 *                          pour ne pas faire clignoter l'ecran a la moindre
 *                          demi-journee de retard
 * @param projectedValue    ou l'utilisateur finira la semaine a ce rythme ;
 *                          {@code null} en debut de semaine, une projection sur
 *                          trois heures d'activite n'ayant aucune valeur
 * @param appreciation      verdict et message, rediges cote serveur, prets a
 *                          afficher
 */
public record GoalProgressResponse(
        UUID goalId,
        GoalType type,
        String unit,
        double targetValue,
        Double currentValue,
        Double completionPercent,
        Double remaining,
        boolean achieved,
        Double elapsedPercent,
        boolean onTrack,
        Double projectedValue,
        Appreciation appreciation) {
}
