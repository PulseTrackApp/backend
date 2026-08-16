package com.pulsetrack.backend.summary;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.motivation.Appreciation;
import com.pulsetrack.backend.motivation.AppreciationTier;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.springframework.stereotype.Component;

/**
 * Confronte un objectif aux totaux reellement realises, et en tire un avis.
 *
 * <p>Deux familles d'objectifs, deux facons de mesurer :
 * <ul>
 *   <li><strong>cumul hebdomadaire</strong> (distance, seances, duree, calories) :
 *       on part de zero chaque lundi et on additionne ;</li>
 *   <li><strong>poids cible</strong> : on ne cumule rien, on se rapproche d'une
 *       valeur depuis un point de depart.</li>
 * </ul>
 *
 * <p><strong>Le pourcentage seul ne dit rien.</strong> Quarante pour cent d'un
 * objectif hebdomadaire, c'est de l'avance le mardi et du retard le samedi. Tout
 * l'interet de l'appreciation est la : elle compare l'avancement au temps ecoule,
 * ce que le pourcentage brut ne fait pas et ce qu'aucun client ne peut deviner
 * sans connaitre le fuseau de l'utilisateur.
 *
 * <p>Classe sans etat ni dependance, testable avec un simple {@code new}.
 */
@Component
public class GoalProgressCalculator {

    private static final double METERS_PER_KM = 1_000d;
    private static final double SECONDS_PER_MINUTE = 60d;

    /**
     * Tolerance, en points, avant de declarer quelqu'un en retard. Sans elle,
     * l'ecran basculerait au rouge des la premiere demi-journee creuse, ce qui
     * decourage au lieu d'alerter.
     */
    static final double ON_TRACK_TOLERANCE = 5d;

    /** Au-dela de cette avance, on parle de bonne semaine et non de conformite. */
    private static final double AHEAD_MARGIN = 15d;

    /** En dessous de ce retard, l'objectif ne se rattrape plus tout seul. */
    private static final double AT_RISK_MARGIN = 25d;

    /**
     * Sous ce dixieme de semaine ecoule, aucune projection n'est rendue : deduire
     * une semaine entiere de trois heures d'activite donnerait un chiffre
     * fantaisiste que l'utilisateur prendrait au serieux.
     */
    private static final double MIN_ELAPSED_FOR_PROJECTION = 0.10;

    /**
     * @param goal             objectif actif
     * @param totals           totaux de la semaine consideree
     * @param baselineWeightKg poids du tout premier releve, point de depart d'un
     *                         objectif de poids ; {@code null} si aucun releve
     * @param currentWeightKg  poids du dernier releve ; {@code null} si aucun
     * @param elapsedFraction  part de la semaine ecoulee, de 0 a 1. Pour une
     *                         semaine passee, vaut 1
     */
    public GoalProgressResponse progressOf(Goal goal,
                                           WorkoutTotals.Normalized totals,
                                           Double baselineWeightKg,
                                           Double currentWeightKg,
                                           double elapsedFraction) {
        if (goal.getType().isWeeklyAccumulation()) {
            return accumulationProgress(goal, totals, clamp(elapsedFraction));
        }
        return weightProgress(goal, baselineWeightKg, currentWeightKg);
    }

    private GoalProgressResponse accumulationProgress(Goal goal,
                                                      WorkoutTotals.Normalized totals,
                                                      double elapsedFraction) {
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
        double elapsedPercent = elapsedFraction * 100;
        double remaining = Math.max(0, target - current);
        boolean achieved = current >= target;
        boolean onTrack = achieved || completion >= elapsedPercent - ON_TRACK_TOLERANCE;

        Double projected = elapsedFraction >= MIN_ELAPSED_FOR_PROJECTION
                ? round(current / elapsedFraction)
                : null;

        return new GoalProgressResponse(
                goal.getId(),
                goal.getType(),
                goal.getType().unit(),
                round(target),
                round(current),
                round(completion),
                round(remaining),
                achieved,
                round(elapsedPercent),
                onTrack,
                projected,
                appreciationOf(goal.getType(), achieved, completion, elapsedPercent,
                        remaining, projected, target));
    }

    /**
     * Progression vers un poids cible, mesuree depuis le premier releve.
     *
     * <p>Sans point de depart, aucun pourcentage n'est calculable : on renvoie le
     * poids courant et un pourcentage nul plutot qu'un chiffre invente.
     *
     * <p>Ni {@code elapsedPercent} ni {@code projectedValue} : un poids cible n'a
     * pas d'echeance hebdomadaire, et projeter une perte de poids sur un rythme
     * de quelques jours ne veut rien dire.
     */
    private GoalProgressResponse weightProgress(Goal goal, Double baselineKg, Double currentKg) {
        double target = goal.getTargetValue();

        if (currentKg == null) {
            return new GoalProgressResponse(goal.getId(), GoalType.TARGET_WEIGHT,
                    GoalType.TARGET_WEIGHT.unit(), round(target), null, null, null, false,
                    null, false, null,
                    Appreciation.noData("Note ton poids une premiere fois pour suivre cet objectif."));
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
                achieved,
                null,
                achieved || (completion != null && completion > 0),
                null,
                weightAppreciation(achieved, completion, remaining, target));
    }

    /**
     * Le verdict sur un objectif hebdomadaire, et une phrase qui dit pourquoi.
     *
     * <p>Le message cite toujours les deux chiffres qui comptent — l'avancement et
     * le temps ecoule — parce que c'est leur ecart qui fait le verdict, et qu'un
     * verdict sans son motif se conteste.
     */
    private Appreciation appreciationOf(GoalType type,
                                        boolean achieved,
                                        double completion,
                                        double elapsedPercent,
                                        double remaining,
                                        Double projected,
                                        double target) {
        String unit = type.unit();

        if (achieved) {
            return new Appreciation(
                    AppreciationTier.EXCELLENT,
                    "Objectif atteint",
                    completion > 105
                            ? "%s de l'objectif : tu l'as depasse.".formatted(Wording.percent(completion))
                            : "Objectif tenu.",
                    "Le prochain palier serait %s %s."
                            .formatted(Wording.decimal(target * 1.1, 1), unit));
        }

        double delta = completion - elapsedPercent;
        String situation = "%s de l'objectif a %s de la semaine ecoulee."
                .formatted(Wording.percent(completion), Wording.percent(elapsedPercent));
        String reste = "Il reste %s %s a faire.".formatted(Wording.decimal(remaining, 1), unit);

        if (delta >= AHEAD_MARGIN) {
            return new Appreciation(AppreciationTier.GOOD, "En avance", situation,
                    projectionAdvice(projected, target, unit));
        }
        if (delta >= -ON_TRACK_TOLERANCE) {
            return new Appreciation(AppreciationTier.ON_TRACK, "Dans les temps", situation,
                    projectionAdvice(projected, target, unit));
        }
        if (delta >= -AT_RISK_MARGIN) {
            return new Appreciation(AppreciationTier.BEHIND, "Un peu en retard", situation, reste);
        }
        // Le pire verdict reste un constat, jamais un reproche : une application
        // qui gronde se desinstalle.
        return new Appreciation(AppreciationTier.AT_RISK, "L'objectif s'eloigne", situation,
                reste + " Une seance de plus change tout.");
    }

    private String projectionAdvice(Double projected, double target, String unit) {
        if (projected == null) {
            return null;
        }
        return projected >= target
                ? "A ce rythme tu finis a %s %s, au-dessus de la cible."
                        .formatted(Wording.decimal(projected, 1), unit)
                : "A ce rythme tu finis a %s %s : il manquerait %s %s."
                        .formatted(Wording.decimal(projected, 1), unit,
                                Wording.decimal(target - projected, 1), unit);
    }

    private Appreciation weightAppreciation(boolean achieved, Double completion, double remaining, double target) {
        if (achieved) {
            return new Appreciation(AppreciationTier.EXCELLENT, "Poids cible atteint",
                    "Tu es a %s kg, ta cible.".formatted(Wording.decimal(target, 1)),
                    "Le maintien compte autant que la descente : garde le rythme des seances.");
        }
        String reste = "Il reste %s kg avant la cible.".formatted(Wording.decimal(remaining, 1));
        if (completion == null) {
            return new Appreciation(AppreciationTier.ON_TRACK, "En route", reste, null);
        }
        if (completion >= 50) {
            return new Appreciation(AppreciationTier.GOOD, "Plus de la moitie du chemin",
                    "%s du chemin parcouru. %s".formatted(Wording.percent(completion), reste), null);
        }
        if (completion > 0) {
            return new Appreciation(AppreciationTier.ON_TRACK, "En route",
                    "%s du chemin parcouru. %s".formatted(Wording.percent(completion), reste), null);
        }
        return new Appreciation(AppreciationTier.BEHIND, "Le chemin n'a pas commence", reste,
                "Le poids bouge lentement : c'est la regularite des seances qui le fait bouger.");
    }

    /** Une fraction de semaine hors de [0, 1] n'existe pas ; on la ramene dedans. */
    private double clamp(double fraction) {
        return Math.max(0, Math.min(1, fraction));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
