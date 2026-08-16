package com.pulsetrack.backend.achievement;

/**
 * Nature d'un record, et regle qui decide qu'il est battu.
 *
 * <p><strong>Pourquoi une marge minimale.</strong> Le GPS tremble. Sans seuil,
 * une sortie identique a la precedente au metre pres ferait tomber un record une
 * fois sur deux, et les felicitations ne voudraient plus rien dire au bout d'une
 * semaine. Chaque record exige donc de depasser le precedent d'une quantite
 * absolue <em>et</em> d'une proportion : la premiere protege les petites valeurs,
 * la seconde les grandes.
 *
 * <p>Constante de protocole : ces identifiants circulent tels quels dans l'API,
 * les renommer casserait les applications publiees.
 */
public enum AchievementKind {

    /**
     * Premiere seance enregistree dans un sport. Ce n'est pas un record battu
     * mais un evenement qui merite d'etre celebre : c'est justement le moment ou
     * l'utilisateur decide s'il revient.
     */
    FIRST_SESSION("Première séance", "session", false, 0d, 0d),

    LONGEST_DISTANCE("Plus longue sortie", "m", false, 50d, 0.01),

    LONGEST_MOVING_DURATION("Plus long temps en mouvement", "s", false, 30d, 0.01),

    /**
     * Meilleure allure moyenne. Plus bas est mieux, et la seance doit faire au
     * moins un kilometre : une allure mesuree sur deux cents metres est du bruit.
     */
    BEST_AVERAGE_PACE("Meilleure allure moyenne", "s/km", true, 2d, 0d),

    HIGHEST_ELEVATION_GAIN("Plus gros dénivelé", "m", false, 10d, 0.01),

    /** Meilleur temps sur un parcours enregistre. Plus bas est mieux. */
    BEST_ROUTE_TIME("Meilleur temps sur un parcours", "s", true, 5d, 0d);

    /** Distance minimale, en metres, sous laquelle une allure n'a pas de sens. */
    public static final double MIN_DISTANCE_FOR_PACE_METERS = 1_000d;

    private final String label;
    private final String unit;
    private final boolean lowerIsBetter;
    private final double minAbsoluteMargin;
    private final double minRelativeMargin;

    AchievementKind(String label,
                    String unit,
                    boolean lowerIsBetter,
                    double minAbsoluteMargin,
                    double minRelativeMargin) {
        this.label = label;
        this.unit = unit;
        this.lowerIsBetter = lowerIsBetter;
        this.minAbsoluteMargin = minAbsoluteMargin;
        this.minRelativeMargin = minRelativeMargin;
    }

    public String label() {
        return label;
    }

    /** {@code m}, {@code s}, {@code s/km} ou {@code session}. */
    public String unit() {
        return unit;
    }

    /** Vrai pour une allure ou un chronometre, ou la plus petite valeur gagne. */
    public boolean lowerIsBetter() {
        return lowerIsBetter;
    }

    /**
     * Le candidat bat-il le precedent record ?
     *
     * @param previous  meilleure valeur connue, ou {@code null} s'il n'y en a
     *                  aucune — auquel cas toute valeur est un record
     * @param candidate valeur de la nouvelle seance
     */
    public boolean beats(Double previous, double candidate) {
        if (previous == null) {
            return true;
        }
        double margin = Math.max(minAbsoluteMargin, Math.abs(previous) * minRelativeMargin);
        return lowerIsBetter
                ? candidate <= previous - margin
                : candidate >= previous + margin;
    }

    /**
     * De combien le record est battu, toujours en positif.
     *
     * <p>Un gain d'allure de douze secondes et un gain de distance de douze
     * metres se lisent tous deux « +12 » a l'ecran : c'est au serveur de
     * redresser le signe, sinon chaque client refait le raisonnement et l'un des
     * deux se trompe.
     */
    public double improvementOver(double previous, double candidate) {
        return lowerIsBetter ? previous - candidate : candidate - previous;
    }
}
