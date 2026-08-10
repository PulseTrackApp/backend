package com.pulsetrack.backend.bodycheckin;

/**
 * Tendance globale du poids sur l'ensemble des releves.
 */
public enum WeightTrend {
    /** Le poids baisse de facon nette. */
    LOSING,
    /** Variation trop faible pour etre distinguee du bruit de mesure. */
    STABLE,
    /** Le poids monte de facon nette. */
    GAINING,
    /** Moins de deux releves : aucune tendance calculable. */
    NOT_ENOUGH_DATA
}
