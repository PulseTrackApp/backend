package com.pulsetrack.backend.summary;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.springframework.stereotype.Component;

/**
 * Confronte un objectif aux totaux reellement realises.
 *
 * <p>Deux familles d'objectifs, deux facons de mesurer :
 * <ul>
 *   <li><strong>cumul hebdomadaire</strong> (distance, seances, duree, calories) :
 *       on part de zero chaque lundi et on additionne ;</li>
 *   <li><strong>poids cible</strong> : on ne cumule rien, on se rapproche d'une
 *       valeur depuis un point de depart.</li>
 * </ul>
 *
 * <p>Classe sans etat ni dependance, testable avec un simple {@code new}.
 */
@Component
public class GoalProgressCalculator {

    private static final double METERS_PER_KM = 1_000d;
    private static final double SECONDS_PER_MINUTE = 60d;

    /**
     * @param goal            objectif actif
     * @param totals          totaux de la semaine consideree
     * @param baselineWeightKg poids du tout premier releve, point de depart d'un
     *                         objectif de poids ; {@code null} si aucun releve
     * @param currentWeightKg  poids du dernier releve ; {@code null} si aucun
     */
    public GoalProgressResponse progressOf(Goal goal,
                                           WorkoutTotals.Normalized totals,
                                           Double baselineWeightKg,
                                           Double currentWeightKg) {
        if (goal.getType().isWeeklyAccumulation()) {
            return accumulationProgress(goal, totals);
        }
        return weightProgress(goal, baselineWeightKg, currentWeightKg);
    }

    private GoalProgressResponse accumulationProgress(Goal goal, WorkoutTotals.Normalized totals) {
        double current = switch (goal.getType()) {
            case WEEKLY_DISTANCE -> totals.distanceMeters() / METERS_PER_KM;
            case WEEKLY_SESSIONS -> totals.sessionCount();
            case WEEKLY_DURATION -> totals.movingDurationSeconds() / SECONDS_PER_MINUTE;
            case WEEKLY_CALORIES -> totals.caloriesBurned();
            case TARGET_WEIGHT -> throw new IllegalStateException("Traite par weightProgress");
        };

        double target = goal.getTargetValue();
        // Pourcentage volontairement non plafonne : depasser son objectif de 20 %
        // merite d'etre affiche, pas ramene a 100 %.
        double completion = target > 0 ? current / target * 100 : 0;

        return new GoalProgressResponse(
                goal.getId(),
                goal.getType(),
                goal.getType().unit(),
                round(target),
                round(current),
                round(completion),
                round(Math.max(0, target - current)),
                current >= target);
    }

    /**
     * Progression vers un poids cible, mesuree depuis le premier releve.
     *
     * <p>Sans point de depart, aucun pourcentage n'est calculable : on renvoie le
     * poids courant et un pourcentage nul plutot qu'un chiffre invente.
     */
    private GoalProgressResponse weightProgress(Goal goal, Double baselineKg, Double currentKg) {
        double target = goal.getTargetValue();

        if (currentKg == null) {
            return new GoalProgressResponse(goal.getId(), GoalType.TARGET_WEIGHT,
                    GoalType.TARGET_WEIGHT.unit(), round(target), null, null, null, false);
        }

        double remaining = Math.abs(currentKg - target);
        boolean losing = baselineKg == null || baselineKg > target;
        boolean achieved = losing ? currentKg <= target : currentKg >= target;

        Double completion = null;
        if (baselineKg != null) {
            double totalToCover = Math.abs(baselineKg - target);
            double covered = losing ? baselineKg - currentKg : currentKg - baselineKg;
            // Depart deja sur la cible : l'objectif est atteint ou il ne l'est plus.
            completion = totalToCover == 0
                    ? (achieved ? 100d : 0d)
                    : Math.max(0, covered / totalToCover * 100);
        }

        return new GoalProgressResponse(
                goal.getId(),
                GoalType.TARGET_WEIGHT,
                GoalType.TARGET_WEIGHT.unit(),
                round(target),
                round(currentKg),
                completion == null ? null : round(completion),
                round(remaining),
                achieved);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
