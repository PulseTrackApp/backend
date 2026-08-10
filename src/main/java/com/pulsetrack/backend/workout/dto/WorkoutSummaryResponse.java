package com.pulsetrack.backend.workout.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.Feeling;

/**
 * Vue allegee pour l'ecran d'historique : tout sauf le trace GPS.
 *
 * <p>Une liste de 20 seances rapatrierait sinon des dizaines de milliers de
 * points, pour une vignette qui n'en affiche aucun.
 */
public record WorkoutSummaryResponse(
        UUID id,
        SportType sportType,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        long movingDurationSeconds,
        double distanceMeters,
        Integer averagePaceSecondsPerKm,
        double averageSpeedKmh,
        double maxSpeedKmh,
        double elevationGainMeters,
        int caloriesBurned,
        Integer perceivedEffort,
        Feeling feeling,
        String note) {
}
