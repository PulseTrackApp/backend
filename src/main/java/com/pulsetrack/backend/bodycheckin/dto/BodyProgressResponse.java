package com.pulsetrack.backend.bodycheckin.dto;

import java.util.List;

import com.pulsetrack.backend.bodycheckin.BmiCategory;
import com.pulsetrack.backend.bodycheckin.WeightTrend;

/**
 * Vue « evolution physique » : la serie a tracer, plus les indicateurs derives.
 *
 * <p>Tous les indicateurs sont nullables : avec un seul releve, il n'y a
 * simplement pas de variation a annoncer. Renvoyer {@code 0.0} laisserait croire
 * a une stagnation mesuree, alors qu'il n'y a rien de mesure du tout.
 *
 * @param series                 releves du plus ancien au plus recent
 * @param totalChangeKg          ecart entre le premier et le dernier releve
 * @param changeSincePreviousKg  ecart entre les deux derniers releves
 * @param averageWeeklyChangeKg  rythme moyen, ramene a la semaine
 */
public record BodyProgressResponse(
        List<BodyCheckInResponse> series,
        int checkInCount,
        Double startWeightKg,
        Double currentWeightKg,
        Double totalChangeKg,
        Double changeSincePreviousKg,
        Double averageWeeklyChangeKg,
        WeightTrend trend,
        Double currentBmi,
        BmiCategory bmiCategory) {
}
