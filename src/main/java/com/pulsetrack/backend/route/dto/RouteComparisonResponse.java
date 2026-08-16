package com.pulsetrack.backend.route.dto;

import java.util.UUID;

/**
 * Ce que vaut la seance qu'on vient d'enregistrer face aux passages precedents
 * sur le meme parcours.
 *
 * <p>Jointe a la reponse d'enregistrement d'une seance : c'est au moment ou
 * l'utilisateur franchit sa ligne d'arrivee que la comparaison l'interesse, pas
 * dans un ecran qu'il faudrait aller chercher.
 *
 * <p><strong>Un ecart negatif veut dire plus rapide.</strong> C'est la convention
 * du chronometre, a garder telle quelle a l'ecran : « −1:00 » en vert.
 *
 * @param attemptNumber                 rang chronologique de ce passage
 * @param bestPreviousDurationSeconds   meilleur temps <em>avant</em> celui-ci ;
 *                                      {@code null} au premier passage
 * @param previousAttemptDurationSeconds temps du passage precedent ; {@code null}
 *                                      au premier
 * @param rank                          place de ce passage au classement, 1 pour
 *                                      le meilleur
 */
public record RouteComparisonResponse(
        UUID routeId,
        String routeName,
        int attemptNumber,
        int attemptCount,
        long durationSeconds,
        Long bestPreviousDurationSeconds,
        Long previousAttemptDurationSeconds,
        Long deltaSecondsVsBest,
        Long deltaSecondsVsPrevious,
        boolean isNewBest,
        int rank,
        String headline,
        String message) {
}
