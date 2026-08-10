package com.pulsetrack.backend.stats.dto;

import java.time.LocalDate;
import java.util.List;

import com.pulsetrack.backend.stats.StatsPeriod;

/**
 * Ecran de statistiques complet pour une periode : le sport et le corps.
 *
 * @param end            dernier jour <strong>inclus</strong> de la periode
 * @param previousPeriod totaux de la periode precedente ; {@code null} pour
 *                       {@code LIFETIME}, qui n'a rien avant elle
 * @param series         serie temporelle continue, intervalles vides compris
 * @param body           evolution du poids et des mensurations sur la periode
 */
public record StatsResponse(
        StatsPeriod period,
        LocalDate start,
        LocalDate end,
        String zone,
        StatsTotals totals,
        StatsTotals previousPeriod,
        List<SportBreakdown> bySport,
        List<StatsBucket> series,
        PersonalRecords records,
        BodyStats body) {
}
