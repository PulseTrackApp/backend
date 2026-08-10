package com.pulsetrack.backend.summary.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Bilan de la semaine, tel qu'affiche par le dashboard.
 *
 * @param weekStart          lundi de la semaine, dans le fuseau demande
 * @param weekEnd            dimanche de la semaine (inclus)
 * @param zone               fuseau utilise pour decouper les jours et la semaine
 * @param sessionCount       nombre de seances
 * @param distanceMeters     distance cumulee
 * @param movingDurationSeconds temps en mouvement cumule
 * @param caloriesBurned     depense energetique cumulee
 * @param elevationGainMeters denivele positif cumule
 * @param previousWeek       ecarts par rapport a la semaine precedente
 * @param goals              progression de chaque objectif actif
 * @param activeDayStreak    nombre de jours consecutifs avec au moins une seance
 */
public record WeeklySummaryResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        String zone,
        int sessionCount,
        double distanceMeters,
        long movingDurationSeconds,
        int caloriesBurned,
        double elevationGainMeters,
        WeeklyComparison previousWeek,
        List<GoalProgressResponse> goals,
        int activeDayStreak) {

    /**
     * Ecarts avec la semaine precedente. Repondre a « je fais mieux ou moins bien
     * que la semaine derniere ? » sans obliger le client a lancer une seconde
     * requete puis a soustraire lui-meme.
     *
     * @param distanceChangePercent variation relative de distance ; {@code null}
     *                              si la semaine precedente etait vide, auquel cas
     *                              un pourcentage n'aurait pas de sens
     */
    public record WeeklyComparison(
            int sessionCountDelta,
            double distanceMetersDelta,
            long movingDurationSecondsDelta,
            int caloriesBurnedDelta,
            Double distanceChangePercent) {
    }
}
