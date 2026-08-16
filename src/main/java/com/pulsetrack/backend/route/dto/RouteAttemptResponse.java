package com.pulsetrack.backend.route.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Un passage sur un parcours, place dans le classement des tentatives.
 *
 * <p>Le classement porte sur le temps <strong>en mouvement</strong> : s'arreter
 * lacer sa chaussure au milieu d'un circuit ne veut pas dire l'avoir couru plus
 * lentement.
 *
 * @param rank               1 pour la meilleure tentative
 * @param deltaSecondsVsBest ecart avec la meilleure ; 0 pour celle-ci et positif
 *                           pour les autres — un temps plus long est un ecart
 *                           positif, comme sur un chronometre
 */
public record RouteAttemptResponse(
        int rank,
        UUID workoutId,
        Instant startedAt,
        long durationSeconds,
        long movingDurationSeconds,
        double distanceMeters,
        Integer averagePaceSecondsPerKm,
        boolean isBest,
        long deltaSecondsVsBest) {
}
