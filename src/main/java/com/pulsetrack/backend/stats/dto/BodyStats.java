package com.pulsetrack.backend.stats.dto;

import java.util.List;

import com.pulsetrack.backend.bodycheckin.BmiCategory;
import com.pulsetrack.backend.bodycheckin.WeightTrend;
import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInResponse;

/**
 * Evolution physique sur la periode analysee.
 *
 * <p>Tout est nullable : sans pesee sur la periode, il n'y a aucune variation a
 * annoncer, et renvoyer zero laisserait croire a une stagnation mesuree.
 *
 * @param series          releves de la periode, du plus ancien au plus recent,
 *                        prets a tracer la courbe (poids, mensurations et IMC)
 * @param changeKg        ecart entre le premier et le dernier releve de la periode
 * @param waistChangeCm   variation du tour de taille sur la periode
 */
public record BodyStats(
        int checkInCount,
        Double startWeightKg,
        Double endWeightKg,
        Double changeKg,
        Double minWeightKg,
        Double maxWeightKg,
        Double averageWeightKg,
        Double averageWeeklyChangeKg,
        WeightTrend trend,
        Double endBmi,
        BmiCategory bmiCategory,
        Double waistChangeCm,
        Double chestChangeCm,
        Double hipsChangeCm,
        List<BodyCheckInResponse> series) {
}
