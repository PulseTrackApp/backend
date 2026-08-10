package com.pulsetrack.backend.workout;

/**
 * Metriques derivees d'une seance, produites par {@link WorkoutMetricsCalculator}.
 *
 * <p>Type dedie plutot que huit valeurs de retour eparpillees : impossible
 * d'intervertir deux parametres au passage, et le calcul reste testable seul.
 *
 * @param durationSeconds          duree totale, pauses comprises
 * @param movingDurationSeconds    duree hors arrets
 * @param distanceMeters           distance parcourue
 * @param averagePaceSecondsPerKm  allure moyenne, {@code null} si distance nulle
 * @param averageSpeedKmh          vitesse moyenne sur le temps en mouvement
 * @param maxSpeedKmh              vitesse maximale observee
 * @param elevationGainMeters      denivele positif cumule
 * @param caloriesBurned           depense energetique estimee
 */
public record WorkoutMetrics(
        long durationSeconds,
        long movingDurationSeconds,
        double distanceMeters,
        Integer averagePaceSecondsPerKm,
        double averageSpeedKmh,
        double maxSpeedKmh,
        double elevationGainMeters,
        int caloriesBurned) {
}
