package com.pulsetrack.backend.stats.dto;

import com.pulsetrack.backend.common.domain.SportType;

/**
 * Part d'un sport dans la periode.
 *
 * @param distanceSharePercent part de la distance totale, pour un diagramme
 *                             circulaire ; nulle si aucune distance n'a ete
 *                             parcourue, auquel cas une part n'a pas de sens
 */
public record SportBreakdown(SportType sport, StatsTotals totals, Double distanceSharePercent) {
}
