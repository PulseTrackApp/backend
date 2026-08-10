package com.pulsetrack.backend.stats.dto;

/**
 * Totaux sportifs d'une periode ou d'un intervalle.
 *
 * @param activeDays nombre de jours distincts avec au moins une seance ; plus
 *                   parlant que le nombre de seances pour juger de la regularite
 */
public record StatsTotals(
        int sessionCount,
        int activeDays,
        double distanceMeters,
        long movingDurationSeconds,
        int caloriesBurned,
        double elevationGainMeters) {

    public static StatsTotals empty() {
        return new StatsTotals(0, 0, 0d, 0L, 0, 0d);
    }
}
