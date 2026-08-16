package com.pulsetrack.backend.rating;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalService;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.rating.dto.RatingResponse;
import com.pulsetrack.backend.summary.ActivityStreakCalculator;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Note l'utilisateur et l'encourage.
 *
 * <p>Trois fenetres de vingt-huit jours sont lues : celle qui est notee, la
 * precedente qui sert a mesurer la progression, et l'avant-precedente qui permet
 * de recalculer la note d'il y a un mois — c'est elle qui donne la tendance. Ni
 * la note ni la tendance ne sont stockees : elles se recalculent a l'identique a
 * chaque appel, ce qui evite d'avoir a les rattraper quand une seance est
 * supprimee.
 */
@Service
public class RatingService {

    private final WorkoutSessionRepository sessions;
    private final GoalService goalService;
    private final RatingCalculator calculator;
    private final ActivityStreakCalculator streakCalculator;

    public RatingService(WorkoutSessionRepository sessions,
                         GoalService goalService,
                         RatingCalculator calculator,
                         ActivityStreakCalculator streakCalculator) {
        this.sessions = sessions;
        this.goalService = goalService;
        this.calculator = calculator;
        this.streakCalculator = streakCalculator;
    }

    /**
     * @param zone fuseau de l'utilisateur, qui decide ou tombent les bornes de
     *             journee — et donc le compte des jours actifs
     */
    @Transactional(readOnly = true)
    public RatingResponse rate(UUID userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        int window = RatingCalculator.WINDOW_DAYS;

        Instant startOfWindow = startOf(today.minusDays(window - 1L), zone);
        Instant startOfPrevious = startOf(today.minusDays(2L * window - 1), zone);
        Instant startOfOlder = startOf(today.minusDays(3L * window - 1), zone);
        Instant endOfWindow = startOf(today.plusDays(1), zone);

        List<WorkoutStatsRow> current = sessions.statsRowsBetween(userId, startOfWindow, endOfWindow);
        List<WorkoutStatsRow> previous = sessions.statsRowsBetween(userId, startOfPrevious, startOfWindow);
        List<WorkoutStatsRow> older = sessions.statsRowsBetween(userId, startOfOlder, startOfPrevious);

        List<Goal> weeklyGoals = goalService.activeGoalsOf(userId).stream()
                // Un poids cible n'est pas un cumul hebdomadaire : le compter
                // comme tel donnerait un objectif jamais tenu quatre semaines de
                // suite, et une note injustement basse.
                .filter(goal -> goal.getType() != GoalType.TARGET_WEIGHT)
                .filter(goal -> goal.getType().isWeeklyAccumulation())
                .toList();

        int streak = streakCalculator.streakOf(
                sessions.activeDaysSince(userId, startOfWindow, zone.getId()), today);

        RatingCalculator.Inputs inputs = new RatingCalculator.Inputs(
                current, previous, activeDaysIn(current, zone), weeklyGoals, streak);

        // La note d'il y a un mois, recalculee avec le meme bareme : c'est le seul
        // point de comparaison honnete, une note stockee aurait ete produite par
        // une version anterieure de la formule.
        Integer previousScore = previous.isEmpty() && older.isEmpty()
                ? null
                : calculator.scoreOf(
                        new RatingCalculator.Inputs(previous, older, activeDaysIn(previous, zone),
                                weeklyGoals, 0),
                        today.minusDays(window), zone);

        return calculator.rate(inputs, previousScore, today, zone);
    }

    /**
     * Jours distincts avec au moins une seance.
     *
     * <p>Compte sur les seances deja lues : une requete de plus pour un chiffre
     * deja derivable serait payee pour rien.
     */
    private int activeDaysIn(List<WorkoutStatsRow> rows, ZoneId zone) {
        return (int) rows.stream()
                .map(row -> row.startedAt().atZone(zone).toLocalDate())
                .distinct()
                .count();
    }

    private Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }
}
