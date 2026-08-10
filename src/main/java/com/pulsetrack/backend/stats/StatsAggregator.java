package com.pulsetrack.backend.stats;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.stats.dto.PersonalRecords;
import com.pulsetrack.backend.stats.dto.SportBreakdown;
import com.pulsetrack.backend.stats.dto.StatsBucket;
import com.pulsetrack.backend.stats.dto.StatsTotals;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.springframework.stereotype.Component;

/**
 * Agrege des seances en totaux, repartitions, series temporelles et records.
 *
 * <p>Classe sans etat ni dependance : toute la logique de statistiques se teste
 * avec des seances construites a la main, sans base ni Spring.
 */
@Component
public class StatsAggregator {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    /**
     * @param rows seances de la periode
     * @param zone fuseau decidant a quel jour appartient chaque seance
     */
    public StatsTotals totalsOf(List<WorkoutStatsRow> rows, ZoneId zone) {
        if (rows.isEmpty()) {
            return StatsTotals.empty();
        }

        Set<LocalDate> activeDays = new HashSet<>();
        double distance = 0;
        long moving = 0;
        int calories = 0;
        double elevation = 0;

        for (WorkoutStatsRow row : rows) {
            activeDays.add(dayOf(row, zone));
            distance += row.distanceMeters();
            moving += row.movingDurationSeconds();
            calories += row.caloriesBurned();
            elevation += row.elevationGainMeters();
        }

        return new StatsTotals(rows.size(), activeDays.size(),
                round(distance), moving, calories, round(elevation));
    }

    /** Repartition par sport, du plus parcouru au moins parcouru. */
    public List<SportBreakdown> breakdownBySport(List<WorkoutStatsRow> rows, ZoneId zone) {
        Map<SportType, List<WorkoutStatsRow>> bySport = new EnumMap<>(SportType.class);
        for (WorkoutStatsRow row : rows) {
            bySport.computeIfAbsent(row.sportType(), sport -> new ArrayList<>()).add(row);
        }

        double totalDistance = rows.stream().mapToDouble(WorkoutStatsRow::distanceMeters).sum();

        return bySport.entrySet().stream()
                .map(entry -> {
                    StatsTotals totals = totalsOf(entry.getValue(), zone);
                    // Pas de pourcentage quand rien n'a ete parcouru : une part
                    // d'un total nul ne veut rien dire.
                    Double share = totalDistance > 0
                            ? round(totals.distanceMeters() / totalDistance * 100)
                            : null;
                    return new SportBreakdown(entry.getKey(), totals, share);
                })
                .sorted((left, right) ->
                        Double.compare(right.totals().distanceMeters(), left.totals().distanceMeters()))
                .toList();
    }

    /**
     * Serie temporelle continue entre deux dates.
     *
     * <p>Chaque intervalle est present, y compris vide : une courbe qui saute les
     * jours sans sport laisserait croire a une activite ininterrompue.
     */
    public List<StatsBucket> seriesOf(List<WorkoutStatsRow> rows,
                                      LocalDate start,
                                      LocalDate endInclusive,
                                      StatsPeriod.BucketSize bucketSize,
                                      ZoneId zone) {
        Map<LocalDate, List<WorkoutStatsRow>> grouped = new java.util.HashMap<>();
        for (WorkoutStatsRow row : rows) {
            grouped.computeIfAbsent(bucketSize.truncate(dayOf(row, zone)), key -> new ArrayList<>())
                    .add(row);
        }

        List<StatsBucket> buckets = new ArrayList<>();
        LocalDate cursor = bucketSize.truncate(start);
        LocalDate limit = bucketSize.truncate(endInclusive);

        while (!cursor.isAfter(limit)) {
            List<WorkoutStatsRow> inBucket = grouped.getOrDefault(cursor, List.of());
            buckets.add(new StatsBucket(cursor, labelOf(cursor, bucketSize), totalsOf(inBucket, zone)));
            cursor = bucketSize.next(cursor);
        }
        return buckets;
    }

    /** Meilleures performances de la periode. */
    public PersonalRecords recordsOf(List<WorkoutStatsRow> rows, ZoneId zone) {
        if (rows.isEmpty()) {
            return PersonalRecords.empty();
        }

        Integer bestPace = rows.stream()
                .map(WorkoutStatsRow::averagePaceSecondsPerKm)
                .filter(pace -> pace != null && pace > 0)
                .min(Integer::compareTo)
                .orElse(null);

        double longestDistance = rows.stream()
                .mapToDouble(WorkoutStatsRow::distanceMeters).max().orElse(0);
        long longestDuration = rows.stream()
                .mapToLong(WorkoutStatsRow::movingDurationSeconds).max().orElse(0);

        Map<LocalDate, Double> distancePerDay = new java.util.HashMap<>();
        for (WorkoutStatsRow row : rows) {
            distancePerDay.merge(dayOf(row, zone), row.distanceMeters(), Double::sum);
        }
        Map.Entry<LocalDate, Double> bestDay = distancePerDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        return new PersonalRecords(
                bestPace,
                longestDistance > 0 ? round(longestDistance) : null,
                longestDuration > 0 ? longestDuration : null,
                bestDay == null ? null : round(bestDay.getValue()),
                bestDay == null ? null : bestDay.getKey());
    }

    /**
     * Jour auquel rattacher une seance.
     *
     * <p>C'est le fuseau de l'utilisateur qui tranche : une course commencee a
     * 00h30 a Ouagadougou appartient a ce jour-la, pas a la veille en UTC.
     */
    private LocalDate dayOf(WorkoutStatsRow row, ZoneId zone) {
        return row.startedAt().atZone(zone).toLocalDate();
    }

    private String labelOf(LocalDate bucketStart, StatsPeriod.BucketSize bucketSize) {
        return bucketSize == StatsPeriod.BucketSize.DAY
                ? bucketStart.format(DAY_LABEL)
                : bucketStart.format(MONTH_LABEL);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
