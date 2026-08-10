package com.pulsetrack.backend.stats;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Fenetre d'analyse des statistiques.
 *
 * <p>Chaque valeur sait calculer ses propres bornes et le pas de sa serie
 * temporelle. Placer cette connaissance dans l'enum evite le {@code switch}
 * disperse dans trois services qui finirait par diverger.
 */
public enum StatsPeriod {

    /** Du lundi au dimanche (ISO 8601), en pas journalier. */
    WEEK(BucketSize.DAY),

    /** Du 1er au dernier jour du mois, en pas journalier. */
    MONTH(BucketSize.DAY),

    /** Du 1er janvier au 31 decembre, en pas mensuel. */
    YEAR(BucketSize.MONTH),

    /**
     * Depuis la toute premiere seance. Pas mensuel, et aucune periode
     * precedente a laquelle se comparer.
     */
    LIFETIME(BucketSize.MONTH);

    private final BucketSize bucketSize;

    StatsPeriod(BucketSize bucketSize) {
        this.bucketSize = bucketSize;
    }

    public BucketSize bucketSize() {
        return bucketSize;
    }

    /** Premier jour de la periode contenant {@code reference}. */
    public LocalDate startOf(LocalDate reference) {
        return switch (this) {
            case WEEK -> reference.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTH -> reference.withDayOfMonth(1);
            case YEAR -> reference.withDayOfYear(1);
            // Bornee par la premiere seance : c'est l'appelant qui la connait.
            case LIFETIME -> reference;
        };
    }

    /** Dernier jour inclus de la periode. */
    public LocalDate endOf(LocalDate start) {
        return switch (this) {
            case WEEK -> start.plusDays(6);
            case MONTH -> start.withDayOfMonth(start.lengthOfMonth());
            case YEAR -> start.withDayOfYear(start.lengthOfYear());
            case LIFETIME -> LocalDate.now();
        };
    }

    /**
     * Debut de la periode precedente, pour la comparaison.
     *
     * @return {@code null} pour {@link #LIFETIME}, qui n'a rien avant elle
     */
    public LocalDate previousStartOf(LocalDate start) {
        return switch (this) {
            case WEEK -> start.minusWeeks(1);
            case MONTH -> start.minusMonths(1);
            case YEAR -> start.minusYears(1);
            case LIFETIME -> null;
        };
    }

    /** Pas de la serie temporelle destinee aux graphiques. */
    public enum BucketSize {
        DAY(ChronoUnit.DAYS),
        MONTH(ChronoUnit.MONTHS);

        private final ChronoUnit unit;

        BucketSize(ChronoUnit unit) {
            this.unit = unit;
        }

        public ChronoUnit unit() {
            return unit;
        }

        /** Ramene une date au debut de son intervalle. */
        public LocalDate truncate(LocalDate date) {
            return this == DAY ? date : date.withDayOfMonth(1);
        }

        public LocalDate next(LocalDate bucketStart) {
            return this == DAY ? bucketStart.plusDays(1) : bucketStart.plusMonths(1);
        }
    }
}
