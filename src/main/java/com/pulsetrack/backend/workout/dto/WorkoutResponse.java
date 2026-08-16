package com.pulsetrack.backend.workout.dto;

import java.util.List;

import com.pulsetrack.backend.achievement.dto.AchievementResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.route.dto.RouteComparisonResponse;

/**
 * Vue detaillee d'une seance : le resume, le trace, et ce que cette sortie a
 * change.
 *
 * <p>Compose le resume au lieu d'en recopier les quinze champs : un champ ajoute
 * a l'historique apparait automatiquement ici.
 *
 * <p>Les trois derniers champs repondent a la question que se pose l'utilisateur
 * a l'arrivee — « est-ce que j'ai fait mieux ? » — sans lui imposer une seconde
 * requete ni un calcul cote client.
 *
 * @param achievements    records tombes pendant cette seance. <strong>Liste non
 *                        vide vaut felicitations</strong> : rien d'autre a
 *                        calculer. Toujours presente, souvent vide
 * @param routeComparison place de cette sortie parmi les passages sur le meme
 *                        parcours ; {@code null} si aucun parcours n'a ete
 *                        declare
 * @param challengeResult verdict du defi que cette seance reglait ; {@code null}
 *                        si aucun defi n'a ete declare, ou s'il etait deja joue
 */
public record WorkoutResponse(
        WorkoutSummaryResponse summary,
        List<GpsPointResponse> gpsPoints,
        List<AchievementResponse> achievements,
        RouteComparisonResponse routeComparison,
        ChallengeResponse.Result challengeResult) {
}
