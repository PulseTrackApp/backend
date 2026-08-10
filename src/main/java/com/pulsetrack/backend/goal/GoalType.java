package com.pulsetrack.backend.goal;

/**
 * Nature d'un objectif, et unite dans laquelle il s'exprime.
 *
 * <p>L'unite est <strong>derivee du type</strong> et non stockee en base : un
 * champ separe pourrait affirmer qu'une distance hebdomadaire se mesure en
 * kilogrammes.
 *
 * <p>La valeur cible est exprimee dans l'unite que l'utilisateur manipule
 * (kilometres, minutes), pas dans l'unite de stockage des seances (metres,
 * secondes). La conversion se fait au moment de comparer, dans
 * {@code GoalProgressCalculator}.
 */
public enum GoalType {

    /** Distance a parcourir dans la semaine, en kilometres. */
    WEEKLY_DISTANCE("km"),

    /** Nombre de seances a realiser dans la semaine. */
    WEEKLY_SESSIONS("seances"),

    /** Temps d'entrainement hebdomadaire, en minutes. */
    WEEKLY_DURATION("min"),

    /** Depense energetique hebdomadaire, en kilocalories. */
    WEEKLY_CALORIES("kcal"),

    /** Poids a atteindre, en kilogrammes. Non periodique. */
    TARGET_WEIGHT("kg");

    private final String unit;

    GoalType(String unit) {
        this.unit = unit;
    }

    public String unit() {
        return unit;
    }

    /**
     * Un objectif hebdomadaire se cumule sur la semaine et se compare a un
     * total ; un poids cible se rapproche depuis un point de depart. Les deux
     * familles ne se mesurent pas de la meme facon.
     */
    public boolean isWeeklyAccumulation() {
        return this != TARGET_WEIGHT;
    }
}
