package com.pulsetrack.backend.bodycheckin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Derive les indicateurs d'evolution physique a partir de la serie de releves.
 *
 * <p>Comme le calculateur de metriques de seance, cette classe est sans etat ni
 * dependance : elle s'instancie avec {@code new} dans un test unitaire.
 */
@Component
public class BodyProgressCalculator {

    /**
     * En dessous de 100 g par semaine, on parle de poids stable. Une balance
     * domestique et l'hydratation du jour introduisent facilement plusieurs
     * centaines de grammes d'ecart : annoncer une tendance sous ce seuil
     * reviendrait a commenter du bruit.
     */
    private static final double STABLE_THRESHOLD_KG_PER_WEEK = 0.1;

    private static final double DAYS_PER_WEEK = 7.0;

    /**
     * @param series   releves tries du plus ancien au plus recent
     * @param heightCm taille du profil, pour l'IMC ; {@code null} si inconnue
     * @return les indicateurs derives, tous nuls si la serie est vide
     */
    public Indicators calculate(List<BodyCheckIn> series, Integer heightCm) {
        if (series.isEmpty()) {
            return new Indicators(null, null, null, null, null,
                    WeightTrend.NOT_ENOUGH_DATA, null, null);
        }

        BodyCheckIn first = series.get(0);
        BodyCheckIn last = series.get(series.size() - 1);

        double startWeight = first.getWeightKg();
        double currentWeight = last.getWeightKg();
        Double currentBmi = bmi(currentWeight, heightCm);

        if (series.size() == 1) {
            // Un seul point : un poids, mais aucune variation a annoncer.
            return new Indicators(startWeight, currentWeight, null, null, null,
                    WeightTrend.NOT_ENOUGH_DATA, currentBmi, category(currentBmi));
        }

        BodyCheckIn previous = series.get(series.size() - 2);
        double totalChange = currentWeight - startWeight;
        double changeSincePrevious = currentWeight - previous.getWeightKg();
        Double averageWeeklyChange = averageWeeklyChange(
                first.getCheckinDate(), last.getCheckinDate(), totalChange);

        return new Indicators(
                round(startWeight, 2),
                round(currentWeight, 2),
                round(totalChange, 2),
                round(changeSincePrevious, 2),
                averageWeeklyChange == null ? null : round(averageWeeklyChange, 2),
                trendOf(averageWeeklyChange),
                currentBmi,
                category(currentBmi));
    }

    /**
     * Ramene la variation totale a un rythme hebdomadaire, pour que deux series
     * de durees differentes soient comparables.
     *
     * @return {@code null} si les deux releves tombent le meme jour, auquel cas
     *         diviser par zero jour n'aurait aucun sens
     */
    private Double averageWeeklyChange(LocalDate from, LocalDate to, double totalChange) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) {
            return null;
        }
        return totalChange / (days / DAYS_PER_WEEK);
    }

    private WeightTrend trendOf(Double averageWeeklyChange) {
        if (averageWeeklyChange == null) {
            return WeightTrend.NOT_ENOUGH_DATA;
        }
        if (averageWeeklyChange <= -STABLE_THRESHOLD_KG_PER_WEEK) {
            return WeightTrend.LOSING;
        }
        if (averageWeeklyChange >= STABLE_THRESHOLD_KG_PER_WEEK) {
            return WeightTrend.GAINING;
        }
        return WeightTrend.STABLE;
    }

    /**
     * Statistiques corporelles restreintes a une periode.
     *
     * <p>Complete {@link #calculate} : celle-ci decrit toute l'histoire, celle-la
     * repond a « qu'est-ce qui a change ce mois-ci ? ».
     *
     * @param series   releves de la periode, tries du plus ancien au plus recent
     * @param heightCm taille du profil, pour l'IMC
     */
    public PeriodIndicators periodStats(List<BodyCheckIn> series, Integer heightCm) {
        if (series.isEmpty()) {
            return new PeriodIndicators(0, null, null, null, null, null, null,
                    null, null, null, WeightTrend.NOT_ENOUGH_DATA, null, null);
        }

        BodyCheckIn first = series.get(0);
        BodyCheckIn last = series.get(series.size() - 1);

        double min = series.stream().mapToDouble(BodyCheckIn::getWeightKg).min().orElseThrow();
        double max = series.stream().mapToDouble(BodyCheckIn::getWeightKg).max().orElseThrow();
        double average = series.stream().mapToDouble(BodyCheckIn::getWeightKg).average().orElseThrow();

        Double change = series.size() > 1 ? last.getWeightKg() - first.getWeightKg() : null;
        Double weekly = change == null ? null
                : averageWeeklyChange(first.getCheckinDate(), last.getCheckinDate(), change);

        return new PeriodIndicators(
                series.size(),
                round(first.getWeightKg(), 2),
                round(last.getWeightKg(), 2),
                change == null ? null : round(change, 2),
                round(min, 2),
                round(max, 2),
                round(average, 2),
                measurementChange(series, BodyCheckIn::getWaistCm),
                measurementChange(series, BodyCheckIn::getChestCm),
                measurementChange(series, BodyCheckIn::getHipsCm),
                trendOf(weekly),
                weekly == null ? null : round(weekly, 2),
                bmi(last.getWeightKg(), heightCm));
    }

    /**
     * Variation d'une mensuration sur la periode.
     *
     * <p>Compare les deux releves ou la mesure est <em>renseignee</em>, pas le
     * premier et le dernier de la serie : le tour de taille n'est pas toujours
     * saisi, et comparer une valeur presente a une valeur absente donnerait un
     * ecart imaginaire.
     */
    private Double measurementChange(List<BodyCheckIn> series,
                                     java.util.function.Function<BodyCheckIn, Double> measurement) {
        List<Double> values = series.stream()
                .map(measurement)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (values.size() < 2) {
            return null;
        }
        return round(values.get(values.size() - 1) - values.get(0), 1);
    }

    /**
     * IMC au moment d'un releve : le poids vient du releve, la taille du profil.
     *
     * @return {@code null} tant que la taille est inconnue
     */
    public Double bmi(double weightKg, Integer heightCm) {
        return BodyMassIndexCalculator.calculate(weightKg, heightCm);
    }

    private BmiCategory category(Double bmi) {
        return BodyMassIndexCalculator.category(bmi);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /**
     * Indicateurs derives de la serie. Tous nullables : voir
     * {@code BodyProgressResponse} pour la raison.
     */
    public record Indicators(
            Double startWeightKg,
            Double currentWeightKg,
            Double totalChangeKg,
            Double changeSincePreviousKg,
            Double averageWeeklyChangeKg,
            WeightTrend trend,
            Double currentBmi,
            BmiCategory bmiCategory) {
    }

    /**
     * Indicateurs corporels restreints a une periode. Tous nullables : sans
     * releve, il n'y a rien a annoncer.
     *
     * @param minWeightKg  poids le plus bas atteint dans la periode
     * @param maxWeightKg  poids le plus haut atteint dans la periode
     * @param waistChangeCm variation du tour de taille, si mesure au moins deux fois
     */
    public record PeriodIndicators(
            int checkInCount,
            Double startWeightKg,
            Double endWeightKg,
            Double changeKg,
            Double minWeightKg,
            Double maxWeightKg,
            Double averageWeightKg,
            Double waistChangeCm,
            Double chestChangeCm,
            Double hipsChangeCm,
            WeightTrend trend,
            Double averageWeeklyChangeKg,
            Double endBmi) {
    }
}
