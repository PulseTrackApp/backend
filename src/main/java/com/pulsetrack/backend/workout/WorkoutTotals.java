package com.pulsetrack.backend.workout;

/**
 * Totaux agreges des seances sur une periode, calcules par la base.
 *
 * <p>Types enveloppes et non primitifs : sur une periode sans aucune seance,
 * {@code sum(...)} en SQL vaut {@code null}, pas zero. Utiliser {@code double}
 * provoquerait une {@code NullPointerException} au deballage. La normalisation
 * se fait dans {@link #orZero()}.
 *
 * <p>Agreger en base plutot que charger les seances : le dashboard resterait
 * instantane avec dix ans d'historique.
 */
public record WorkoutTotals(
        Long sessionCount,
        Double distanceMeters,
        Long movingDurationSeconds,
        Long caloriesBurned,
        Double elevationGainMeters) {

    /** Version sans valeur nulle, prete a etre exposee ou comparee. */
    public Normalized orZero() {
        return new Normalized(
                sessionCount == null ? 0 : sessionCount.intValue(),
                distanceMeters == null ? 0d : distanceMeters,
                movingDurationSeconds == null ? 0L : movingDurationSeconds,
                caloriesBurned == null ? 0 : caloriesBurned.intValue(),
                elevationGainMeters == null ? 0d : elevationGainMeters);
    }

    public record Normalized(
            int sessionCount,
            double distanceMeters,
            long movingDurationSeconds,
            int caloriesBurned,
            double elevationGainMeters) {
    }
}
