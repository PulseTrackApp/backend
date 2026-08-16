package com.pulsetrack.backend.rating;

/**
 * Palier atteint par l'utilisateur.
 *
 * <p>Constante de protocole : le client choisit badge et couleur la-dessus.
 *
 * <p>{@link #NEW} n'est pas un mauvais palier, c'est l'absence de palier. Un
 * compte sans seance ne recoit <strong>pas</strong> zero : noter zero quelqu'un
 * qui vient d'arriver est le plus sur moyen de le perdre.
 */
public enum RatingTier {

    /** Aucune seance : rien a noter, tout a accueillir. */
    NEW(null, 0),

    STARTING("Débutant", 1),
    REGULAR("Régulier", 40),
    SOLID("Solide", 55),
    STRONG("Costaud", 70),
    ATHLETE("Athlète", 85);

    private final String title;
    private final int minimumScore;

    RatingTier(String title, int minimumScore) {
        this.title = title;
        this.minimumScore = minimumScore;
    }

    /** Titre affichable ; {@code null} pour {@link #NEW}, qui n'en a pas. */
    public String title() {
        return title;
    }

    public int minimumScore() {
        return minimumScore;
    }

    /** Palier correspondant a une note. */
    public static RatingTier of(int score) {
        RatingTier found = STARTING;
        for (RatingTier tier : values()) {
            if (tier != NEW && score >= tier.minimumScore) {
                found = tier;
            }
        }
        return found;
    }

    /** Palier suivant, ou vide au sommet : il faut bien que l'echelle s'arrete. */
    public RatingTier next() {
        return switch (this) {
            case NEW -> STARTING;
            case STARTING -> REGULAR;
            case REGULAR -> SOLID;
            case SOLID -> STRONG;
            case STRONG, ATHLETE -> ATHLETE;
        };
    }

    /**
     * Note en lettre, pour un affichage court.
     *
     * <p>Echelle deliberement genereuse dans le bas : la note doit encourager a
     * revenir, pas sanctionner. Un « E » ne s'obtient qu'en ne faisant presque
     * rien pendant quatre semaines.
     */
    public static String gradeOf(int score) {
        if (score >= 90) {
            return "A+";
        }
        if (score >= 80) {
            return "A";
        }
        if (score >= 65) {
            return "B";
        }
        if (score >= 50) {
            return "C";
        }
        return score >= 35 ? "D" : "E";
    }
}
