package com.pulsetrack.backend.summary;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.goal.GoalService;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;
import com.pulsetrack.backend.workout.WorkoutStatsRow;
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
    private final WeeklyAppreciator appreciator;

    public WeeklySummaryService(WorkoutSessionRepository sessions,
                                GoalService goalService,
                                BodyCheckInService bodyCheckInService,
                                GoalProgressCalculator goalProgressCalculator,
                                ActivityStreakCalculator streakCalculator,
                                WeeklyAppreciator appreciator) {
        this.sessions = sessions;
        this.goalService = goalService;
        this.bodyCheckInService = bodyCheckInService;
        this.goalProgressCalculator = goalProgressCalculator;
        this.streakCalculator = streakCalculator;
        this.appreciator = appreciator;
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
        double elapsed = elapsedFractionOf(monday, zone);

        List<GoalProgressResponse> goals = goalProgress(userId, current, elapsed);

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
                goals,
                streak(userId, today, zone),
                dailyBreakdown(userId, monday, zone),
                appreciator.appreciate(current, previous, goals, elapsed));
    }

    /**
     * Part de la semaine deja ecoulee, de 0 a 1.
     *
     * <p>C'est la reference contre laquelle un avancement se juge : 40 % d'un
     * objectif le mardi est en avance, le samedi c'est en retard. Le calcul se
     * fait a la seconde et non a la journee, sans quoi le lundi matin
     * afficherait deja un septieme de semaine consommee.
     *
     * <p>Une semaine passee vaut 1, une semaine future 0.
     */
    private double elapsedFractionOf(LocalDate monday, ZoneId zone) {
        Instant start = monday.atStartOfDay(zone).toInstant();
        Instant end = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        if (!now.isAfter(start)) {
            return 0;
        }
        if (!now.isBefore(end)) {
            return 1;
        }
        return (double) (now.getEpochSecond() - start.getEpochSecond())
                / (end.getEpochSecond() - start.getEpochSecond());
    }

    /**
     * Les sept jours de la semaine, jours vides compris.
     *
     * <p>Les seances sont lues en projection legere puis regroupees en memoire
     * plutot qu'agregees en base : une semaine en compte au plus quelques
     * dizaines, et le decoupage en jours depend du fuseau de l'utilisateur, ce
     * qui obligerait sinon a une seconde requete native.
     */
    private List<WeeklySummaryResponse.DayTotals> dailyBreakdown(UUID userId, LocalDate monday, ZoneId zone) {
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(zone).toInstant();

        Map<LocalDate, List<WorkoutStatsRow>> byDay = sessions.statsRowsBetween(userId, from, to).stream()
                .collect(Collectors.groupingBy(row -> row.startedAt().atZone(zone).toLocalDate()));

        List<WeeklySummaryResponse.DayTotals> days = new ArrayList<>(7);
        for (int offset = 0; offset < 7; offset++) {
            LocalDate day = monday.plusDays(offset);
            List<WorkoutStatsRow> rows = byDay.getOrDefault(day, List.of());
            days.add(new WeeklySummaryResponse.DayTotals(
                    day,
                    day.getDayOfWeek(),
                    rows.size(),
                    round(rows.stream().mapToDouble(WorkoutStatsRow::distanceMeters).sum()),
                    rows.stream().mapToLong(WorkoutStatsRow::movingDurationSeconds).sum(),
                    rows.stream().mapToInt(WorkoutStatsRow::caloriesBurned).sum()));
        }
        return List.copyOf(days);
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

    private List<GoalProgressResponse> goalProgress(UUID userId,
                                                    WorkoutTotals.Normalized totals,
                                                    double elapsedFraction) {
        BodyCheckInService.WeightRange weights = bodyCheckInService.weightRangeOf(userId).orElse(null);
        Double baseline = weights == null ? null : weights.baselineKg();
        Double currentWeight = weights == null ? null : weights.currentKg();

        return goalService.activeGoalsOf(userId).stream()
                .map(goal -> goalProgressCalculator.progressOf(
                        goal, totals, baseline, currentWeight, elapsedFraction))
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
