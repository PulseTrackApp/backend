package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Meilleures performances connues dans un sport, avec la seance qui les detient.
 *
 * <p><strong>Recalculees a la lecture, jamais stockees.</strong> Un record garde
 * en base deviendrait faux le jour ou la seance qui le detenait est supprimee, et
 * l'utilisateur verrait s'afficher indefiniment un chiffre que plus rien ne
 * justifie. Le cout est celui d'une projection sur les seances d'un sport, sans
 * les traces : negligeable a l'echelle d'un suivi personnel.
 *
 * <p>Ne pas confondre avec {@link WorkoutAchievement} : celui-ci enregistre qu'un
 * record est <em>tombe</em> un jour donne, ce qui reste vrai pour toujours.
 *
 * @param sessionCount   nombre de seances du sport
 * @param firstSessionAt debut de la toute premiere ; {@code null} sans seance
 * @param holders        detenteur de chaque record ; une categorie absente
 *                       signifie qu'aucune seance ne permet de la mesurer
 */
public record SportBests(int sessionCount, Instant firstSessionAt, Map<AchievementKind, RecordHolder> holders) {

    /** Seance qui detient un record, et depuis quand. */
    public record RecordHolder(double value, UUID workoutId, Instant achievedAt) {
    }

    public static SportBests empty() {
        return new SportBests(0, null, Map.of());
    }

    /**
     * @param rows seances du sport, dans n'importe quel ordre
     */
    public static SportBests from(List<SportPerformanceRow> rows) {
        if (rows.isEmpty()) {
            return empty();
        }

        Map<AchievementKind, RecordHolder> holders = new EnumMap<>(AchievementKind.class);
        Instant first = null;

        for (SportPerformanceRow row : rows) {
            if (first == null || row.startedAt().isBefore(first)) {
                first = row.startedAt();
            }
            // Une seance sans distance ne concourt a aucun record de distance,
            // mais elle compte dans la duree et le denivele : une seance de
            // renforcement en salle a bien dure une heure.
            if (row.distanceMeters() > 0) {
                consider(holders, AchievementKind.LONGEST_DISTANCE, row.distanceMeters(), row);
            }
            if (row.movingDurationSeconds() > 0) {
                consider(holders, AchievementKind.LONGEST_MOVING_DURATION, row.movingDurationSeconds(), row);
            }
            if (row.elevationGainMeters() > 0) {
                consider(holders, AchievementKind.HIGHEST_ELEVATION_GAIN, row.elevationGainMeters(), row);
            }
            // Seuil de distance applique des la constitution du record, et pas
            // seulement a la comparaison : une allure eclair tenue sur trois
            // cents metres deviendrait sinon un record imbattable a vie.
            if (row.averagePaceSecondsPerKm() != null
                    && row.distanceMeters() >= AchievementKind.MIN_DISTANCE_FOR_PACE_METERS) {
                consider(holders, AchievementKind.BEST_AVERAGE_PACE, row.averagePaceSecondsPerKm(), row);
            }
        }

        return new SportBests(rows.size(), first, Map.copyOf(holders));
    }

    /** Meilleure valeur connue pour cette categorie, ou vide s'il n'y en a pas. */
    public Optional<RecordHolder> holderOf(AchievementKind kind) {
        return Optional.ofNullable(holders.get(kind));
    }

    /** @return la valeur du record, ou {@code null} : c'est ce qu'attend {@link AchievementKind#beats} */
    public Double valueOf(AchievementKind kind) {
        RecordHolder holder = holders.get(kind);
        return holder == null ? null : holder.value();
    }

    public boolean isEmpty() {
        return sessionCount == 0;
    }

    private static void consider(Map<AchievementKind, RecordHolder> holders,
                                 AchievementKind kind,
                                 double value,
                                 SportPerformanceRow row) {
        RecordHolder current = holders.get(kind);
        boolean better = current == null
                || (kind.lowerIsBetter() ? value < current.value() : value > current.value());
        if (better) {
            holders.put(kind, new RecordHolder(value, row.workoutId(), row.startedAt()));
        }
    }
}
