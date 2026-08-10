package com.pulsetrack.backend.bodycheckin;

/**
 * Classification indicative de l'IMC selon les seuils de l'OMS.
 *
 * <p><strong>Ce n'est pas un diagnostic.</strong> L'IMC ignore la composition
 * corporelle : un sportif musclé y apparait frequemment en surpoids. La spec
 * produit interdit explicitement tout propos medical ; cette valeur sert a
 * situer un ordre de grandeur, rien de plus.
 */
public enum BmiCategory {
    UNDERWEIGHT,
    NORMAL,
    OVERWEIGHT,
    OBESE;

    public static BmiCategory of(double bmi) {
        if (bmi < 18.5) {
            return UNDERWEIGHT;
        }
        if (bmi < 25.0) {
            return NORMAL;
        }
        if (bmi < 30.0) {
            return OVERWEIGHT;
        }
        return OBESE;
    }
}
