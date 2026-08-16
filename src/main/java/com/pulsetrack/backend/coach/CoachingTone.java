package com.pulsetrack.backend.coach;

/**
 * Registre de langage du coach. Influence uniquement la formulation des
 * conseils, jamais leur contenu ni les garde-fous de securite.
 */
public enum CoachingTone {

    /** Chaleureux, encourageant, tolerant sur les semaines creuses. */
    ENCOURAGING("bienveillant et encourageant, qui valorise les progrès même modestes"),

    /** Factuel, centre sur les chiffres, sans commentaire affectif. */
    FACTUAL("factuel et direct, qui s'en tient aux chiffres et aux faits"),

    /** Exigeant, oriente performance, sans complaisance. */
    DEMANDING("exigeant et oriente performance, qui pousse a progresser sans complaisance");

    private final String promptDescription;

    CoachingTone(String promptDescription) {
        this.promptDescription = promptDescription;
    }

    /** Formulation injectee dans l'instruction systeme envoyee au modele. */
    public String promptDescription() {
        return promptDescription;
    }
}
