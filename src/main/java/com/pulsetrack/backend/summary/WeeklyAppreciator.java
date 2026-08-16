package com.pulsetrack.backend.summary;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.pulsetrack.backend.motivation.Appreciation;
import com.pulsetrack.backend.motivation.AppreciationTier;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.springframework.stereotype.Component;

/**
 * Rend un avis d'ensemble sur la semaine.
 *
 * <p>Deux references, dans cet ordre : les objectifs quand il y en a, la semaine
 * precedente sinon. Un objectif que l'utilisateur s'est fixe lui-meme est le seul
 * etalon qu'il reconnait ; a defaut, se comparer a soi-meme reste plus juste que
 * de comparer a une norme que personne n'a demandee.
 *
 * <p><strong>Aucun verdict n'accuse.</strong> Le pire, {@code AT_RISK}, constate
 * qu'un objectif ne sera pas tenu sans effort net. Une application de sport qui
 * gronde se desinstalle, et celui qui a le plus besoin d'encouragement est
 * precisement celui qui a le moins couru.
 *
 * <p>Classe sans etat ni dependance : eprouvable avec des totaux choisis.
 */
@Component
public class WeeklyAppreciator {

    /** Avant ce stade de la semaine, ne pas avoir couru n'a rien d'inquietant. */
    private static final double EARLY_WEEK = 0.4;

    /** Hausse de volume au-dela de laquelle on parle de progression. */
    private static final double PROGRESS_MARGIN = 0.10;

    /**
     * @param current         totaux de la semaine en cours
     * @param previous        totaux de la semaine precedente
     * @param goals           progression des objectifs actifs, deja appreciee
     * @param elapsedFraction part de la semaine ecoulee, de 0 a 1
     */
    public Appreciation appreciate(WorkoutTotals.Normalized current,
                                   WorkoutTotals.Normalized previous,
                                   List<GoalProgressResponse> goals,
                                   double elapsedFraction) {

        if (current.sessionCount() == 0) {
            return emptyWeek(elapsedFraction, goals);
        }

        String done = "%s en %s".formatted(
                Wording.distance(current.distanceMeters()),
                Wording.plural(current.sessionCount(), "seance", "seances"));
        String versus = comparedToPrevious(current, previous);

        return goals.isEmpty()
                ? withoutGoals(current, previous, done, versus)
                : withGoals(goals, done, versus);
    }

    /**
     * Semaine sans aucune sortie. Le ton depend entierement du moment : mardi
     * matin, il n'y a rien a signaler ; dimanche soir, il y a quelque chose a
     * dire, mais sans reproche.
     */
    private Appreciation emptyWeek(double elapsedFraction, List<GoalProgressResponse> goals) {
        if (elapsedFraction < EARLY_WEEK) {
            return new Appreciation(
                    AppreciationTier.ON_TRACK,
                    "La semaine commence",
                    "Rien encore cette semaine, et c'est normal a ce stade.",
                    "Vingt minutes suffisent a lancer une semaine.");
        }
        return new Appreciation(
                goals.isEmpty() ? AppreciationTier.BEHIND : AppreciationTier.AT_RISK,
                "Semaine sans sortie",
                "Aucune seance enregistree cette semaine.",
                "Le plus dur est de sortir : une marche de vingt minutes compte deja.");
    }

    private Appreciation withGoals(List<GoalProgressResponse> goals, String done, String versus) {
        List<GoalProgressResponse> measurable = goals.stream()
                .filter(goal -> goal.completionPercent() != null)
                .toList();

        if (measurable.isEmpty()) {
            return new Appreciation(AppreciationTier.ON_TRACK, "Semaine en cours",
                    done + versus, null);
        }

        if (measurable.stream().allMatch(GoalProgressResponse::achieved)) {
            return new Appreciation(AppreciationTier.EXCELLENT, "Tous les objectifs tenus",
                    done + versus,
                    "Le moment est bien choisi pour relever la cible de la semaine prochaine.");
        }

        // On ne cite que l'objectif le plus en retard : un message qui enumere
        // quatre manques se fait ignorer, un qui en nomme un se lit.
        Optional<GoalProgressResponse> worst = measurable.stream()
                .filter(goal -> !goal.achieved())
                .min(Comparator.comparingDouble(GoalProgressResponse::completionPercent));

        boolean allOnTrack = measurable.stream().allMatch(GoalProgressResponse::onTrack);
        String advice = worst
                .map(goal -> "Il reste %s %s pour tenir ton objectif de la semaine."
                        .formatted(Wording.decimal(goal.remaining(), 1), goal.unit()))
                .orElse(null);

        if (allOnTrack) {
            return new Appreciation(AppreciationTier.GOOD, "Bonne semaine", done + versus, advice);
        }

        // Le verdict d'ensemble ne peut pas etre meilleur que celui de l'objectif
        // le plus mal en point : afficher « bonne semaine » au-dessus d'une jauge
        // rouge ferait douter de tout le reste.
        AppreciationTier tier = worst
                .map(goal -> goal.appreciation().tier())
                .orElse(AppreciationTier.ON_TRACK);

        return new Appreciation(tier, headlineOf(tier), done + versus, advice);
    }

    private Appreciation withoutGoals(WorkoutTotals.Normalized current,
                                      WorkoutTotals.Normalized previous,
                                      String done,
                                      String versus) {
        // Premiere semaine mesurable : rien a quoi se comparer, et rien de moins
        // encourageant que de se voir reprocher un point de depart.
        if (previous.sessionCount() == 0) {
            return new Appreciation(AppreciationTier.GOOD, "Semaine lancee", done + ".",
                    "Fixe-toi un objectif hebdomadaire : la semaine prochaine aura une reference.");
        }

        double ratio = previous.distanceMeters() > 0
                ? current.distanceMeters() / previous.distanceMeters()
                : Double.MAX_VALUE;

        if (ratio >= 1 + PROGRESS_MARGIN) {
            return new Appreciation(AppreciationTier.GOOD, "Mieux que la semaine derniere",
                    done + versus, null);
        }
        if (ratio >= 1 - PROGRESS_MARGIN) {
            return new Appreciation(AppreciationTier.ON_TRACK, "Semaine reguliere",
                    done + versus, null);
        }
        return new Appreciation(AppreciationTier.BEHIND, "En dessous de la semaine derniere",
                done + versus,
                "Une sortie de plus suffirait a repasser au-dessus.");
    }

    private String comparedToPrevious(WorkoutTotals.Normalized current, WorkoutTotals.Normalized previous) {
        double delta = current.distanceMeters() - previous.distanceMeters();
        if (previous.sessionCount() == 0) {
            return ".";
        }
        if (Math.abs(delta) < 100) {
            return ", soit autant que la semaine derniere.";
        }
        return delta > 0
                ? ", soit %s de plus que la semaine derniere.".formatted(Wording.distance(delta))
                : ", soit %s de moins que la semaine derniere.".formatted(Wording.distance(-delta));
    }

    private String headlineOf(AppreciationTier tier) {
        return switch (tier) {
            case EXCELLENT -> "Objectifs depasses";
            case GOOD -> "Bonne semaine";
            case ON_TRACK -> "Dans les temps";
            case BEHIND -> "Un peu en retard";
            case AT_RISK -> "Les objectifs s'eloignent";
            case NO_DATA -> "Semaine en cours";
        };
    }
}
