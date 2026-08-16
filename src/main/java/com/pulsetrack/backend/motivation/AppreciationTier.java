package com.pulsetrack.backend.motivation;

/**
 * Verdict porte sur un effort, du plus flatteur au plus inquiet.
 *
 * <p>Constante de protocole : le client choisit couleur, icone et animation
 * la-dessus. Renommer une valeur casserait les applications deja publiees.
 *
 * <p>Il n'existe volontairement <strong>aucun niveau accusateur</strong>. Le pire
 * verdict, {@link #AT_RISK}, constate qu'un objectif ne sera pas tenu sans effort
 * net ; il ne reproche rien. Une application de sport qui gronde se desinstalle.
 */
public enum AppreciationTier {

    /** Objectif depasse, performance remarquable. */
    EXCELLENT,

    /** Nettement au-dessus de ce qu'il fallait a ce stade. */
    GOOD,

    /** Conforme a ce qu'il faut a ce stade : ni avance, ni retard. */
    ON_TRACK,

    /** En retard, mais l'ecart se comble encore. */
    BEHIND,

    /** L'objectif ne sera pas tenu sans un effort net. */
    AT_RISK,

    /** Pas assez de donnees pour juger. Ne rien affirmer vaut mieux qu'inventer. */
    NO_DATA
}
