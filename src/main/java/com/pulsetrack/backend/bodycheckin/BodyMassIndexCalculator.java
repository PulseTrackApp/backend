package com.pulsetrack.backend.bodycheckin;

/**
 * Calculs IMC partages entre le profil initial et les releves physiques.
 */
public final class BodyMassIndexCalculator {

    private BodyMassIndexCalculator() {
    }

    /**
     * @return IMC indicatif arrondi a une decimale, ou {@code null} sans taille exploitable
     */
    public static Double calculate(double weightKg, Integer heightCm) {
        if (heightCm == null || heightCm <= 0) {
            return null;
        }
        double heightMeters = heightCm / 100.0;
        return round(weightKg / (heightMeters * heightMeters), 1);
    }

    public static BmiCategory category(Double bmi) {
        return bmi == null ? null : BmiCategory.of(bmi);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
