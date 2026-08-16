package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection legere d'une seance, reduite a ce qui peut faire un record.
 *
 * <p>Le trace GPS n'y figure pas : chercher un record ne demande pas de charger
 * des milliers de points, et le faire rendrait l'enregistrement d'une seance
 * plus couteux a chaque nouvelle sortie de l'utilisateur.
 *
 * @param averagePaceSecondsPerKm {@code null} quand la seance n'a pas de
 *                                distance : il n'y a alors pas d'allure
 */
public record SportPerformanceRow(
        UUID workoutId,
        Instant startedAt,
        double distanceMeters,
        long movingDurationSeconds,
        Integer averagePaceSecondsPerKm,
        double elevationGainMeters) {
}
