package com.pulsetrack.backend.stats.dto;

import java.time.LocalDate;

/**
 * Meilleures performances de la periode. Toutes nullables : sans seance, il n'y
 * a pas de record, et renvoyer zero laisserait croire a un record nul.
 *
 * @param bestPaceSecondsPerKm       meilleure allure moyenne sur une seance
 * @param longestDistanceMeters      plus longue sortie
 * @param longestMovingDurationSeconds plus longue duree en mouvement
 * @param bestDayDistanceMeters      meilleure journee cumulee
 * @param bestDay                    date de cette journee
 */
public record PersonalRecords(
        Integer bestPaceSecondsPerKm,
        Double longestDistanceMeters,
        Long longestMovingDurationSeconds,
        Double bestDayDistanceMeters,
        LocalDate bestDay) {

    public static PersonalRecords empty() {
        return new PersonalRecords(null, null, null, null, null);
    }
}
