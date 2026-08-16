package com.pulsetrack.backend.achievement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.achievement.AchievementKind;
import com.pulsetrack.backend.common.domain.SportType;

/**
 * Records courants d'un sport.
 *
 * <p>Recalcules a chaque lecture sur tout l'historique du sport : une seance
 * supprimee disparait donc bien des records, la ou une valeur figee en base
 * continuerait d'afficher un chiffre que plus rien ne justifie.
 *
 * @param records categories mesurables ; vide si aucune seance du sport ne porte
 *                de distance, de duree ni de denivele
 */
public record SportRecordsResponse(
        SportType sportType,
        int sessionCount,
        Instant firstSessionAt,
        List<RecordResponse> records) {

    /**
     * @param value   valeur du record, dans {@code unit}
     * @param workoutId seance qui le detient, pour ouvrir son detail d'un geste
     */
    public record RecordResponse(
            AchievementKind kind,
            String label,
            String unit,
            double value,
            UUID workoutId,
            Instant achievedAt) {
    }
}
