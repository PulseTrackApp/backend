package com.pulsetrack.backend.summary;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.goal.GoalService;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bilan hebdomadaire du dashboard : totaux de la semaine, comparaison avec la
 * precedente, progression des objectifs et serie de jours actifs.
 *
 * <p>Ce service ne detient aucune donnee : il orchestre les agregats des
 * seances, les objectifs et les releves physiques en une seule reponse, pour que
 * le dashboard mobile n'ait qu'un appel a faire.
 */
@Service
public class WeeklySummaryService {

    /**
     * Fenetre de recherche de la serie d'activite. Un an couvre tous les usages
     * realistes, et borne le cout de la requete quel que soit l'historique.
     */
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final WorkoutSessionRepository sessions;
    private final GoalService goalService;
    private final BodyCheckInService bodyCheckInService;
    private final GoalProgressCalculator goalProgressCalculator;
    private final ActivityStreakCalculator streakCalculator;

    public WeeklySummaryService(WorkoutSessionRepository sessions,
                                GoalService goalService,
                                BodyCheckInService bodyCheckInService,
                                GoalProgressCalculator goalProgressCalculator,
                                ActivityStreakCalculator streakCalculator) {
        this.sessions = sessions;
        this.goalService = goalService;
        this.bodyCheckInService = bodyCheckInService;
        this.goalProgressCalculator = goalProgressCalculator;
        this.streakCalculator = streakCalculator;
    }

    /**
     * @param weekStart jour de reference ; ramene au lundi de sa semaine. Par
     *                  defaut, la semaine en cours
     * @param zone      fuseau de l'utilisateur, qui decide ou tombent les bornes
     *                  de semaine et de journee
     */
    @Transactional(readOnly = true)
    public WeeklySummaryResponse summarize(UUID userId, LocalDate weekStart, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate monday = mondayOf(weekStart == null ? today : weekStart);
        LocalDate previousMonday = monday.minusWeeks(1);

        WorkoutTotals.Normalized current = totalsOfWeek(userId, monday, zone);
        WorkoutTotals.Normalized previous = totalsOfWeek(userId, previousMonday, zone);

        return new WeeklySummaryResponse(
                monday,
                monday.plusDays(6),
                zone.getId(),
                current.sessionCount(),
                current.distanceMeters(),
                current.movingDurationSeconds(),
                current.caloriesBurned(),
                current.elevationGainMeters(),
                compare(current, previous),
                goalProgress(userId, current),
                streak(userId, today, zone));
    }

    private WorkoutTotals.Normalized totalsOfWeek(UUID userId, LocalDate monday, ZoneId zone) {
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        return sessions.totalsBetween(userId, from, to).orZero();
    }

    private WeeklySummaryResponse.WeeklyComparison compare(WorkoutTotals.Normalized current,
                                                           WorkoutTotals.Normalized previous) {
        // Pas de pourcentage si la semaine precedente etait vide : passer de 0 a
        // 10 km n'est pas une hausse de « l'infini pour cent ».
        Double changePercent = previous.distanceMeters() > 0
                ? round((current.distanceMeters() - previous.distanceMeters())
                        / previous.distanceMeters() * 100)
                : null;

        return new WeeklySummaryResponse.WeeklyComparison(
                current.sessionCount() - previous.sessionCount(),
                round(current.distanceMeters() - previous.distanceMeters()),
                current.movingDurationSeconds() - previous.movingDurationSeconds(),
                current.caloriesBurned() - previous.caloriesBurned(),
                changePercent);
    }

    private List<GoalProgressResponse> goalProgress(UUID userId, WorkoutTotals.Normalized totals) {
        BodyCheckInService.WeightRange weights = bodyCheckInService.weightRangeOf(userId).orElse(null);
        Double baseline = weights == null ? null : weights.baselineKg();
        Double currentWeight = weights == null ? null : weights.currentKg();

        return goalService.activeGoalsOf(userId).stream()
                .map(goal -> goalProgressCalculator.progressOf(goal, totals, baseline, currentWeight))
                .toList();
    }

    private int streak(UUID userId, LocalDate today, ZoneId zone) {
        Instant from = today.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(zone).toInstant();
        List<LocalDate> activeDays = sessions.activeDaysSince(userId, from, zone.getId());
        return streakCalculator.streakOf(activeDays, today);
    }

    /**
     * ISO 8601 : la semaine commence le lundi. Fixer cette convention cote
     * serveur evite que deux clients ne decoupent la semaine differemment.
     */
    private LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
