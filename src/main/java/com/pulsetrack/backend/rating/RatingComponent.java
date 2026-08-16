package com.pulsetrack.backend.rating;

/**
 * Les quatre choses qu'on note, et ce qu'elles pesent.
 *
 * <p>Le poids le plus fort va a la <strong>regularite</strong>, pas au volume :
 * c'est elle qui fait progresser et c'est elle qui tient dans la duree. Noter
 * d'abord les kilometres reviendrait a feliciter la sortie heroique du dimanche
 * qui n'est jamais suivie d'une deuxieme.
 *
 * <p>Les poids somment a 100. Quand une composante n'est pas mesurable — pas
 * d'objectif fixe, par exemple — les autres sont renormalisees, faute de quoi la
 * note serait plafonnee a 75 pour une raison que l'utilisateur ne verrait pas.
 *
 * <p>Constante de protocole : {@code key} circule tel quel dans l'API.
 */
public enum RatingComponent {

    REGULARITY("Regularite", 30),
    VOLUME("Volume", 25),
    GOALS("Objectifs", 25),
    PROGRESSION("Progression", 20);

    private final String label;
    private final int weight;

    RatingComponent(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public int weight() {
        return weight;
    }
}
