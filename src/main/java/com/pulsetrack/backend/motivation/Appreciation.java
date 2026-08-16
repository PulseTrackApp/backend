package com.pulsetrack.backend.motivation;

/**
 * Avis rendu sur un effort, pret a etre affiche.
 *
 * <p>Le texte est redige ici, en francais, et non compose par le client : les
 * chiffres et la phrase qui les commente doivent venir du meme endroit, sinon ils
 * finissent par ne plus s'accorder. C'est aussi ce qui garantit qu'Android et iOS
 * felicitent avec les memes mots.
 *
 * <p><strong>Rien de tout cela ne passe par l'assistant.</strong> Une
 * felicitation ne doit dependre d'aucune cle tierce, ne rien couter, et ne pas
 * faire attendre deux secondes quelqu'un qui vient de franchir sa ligne
 * d'arrivee.
 *
 * @param tier     verdict, pour le choix des couleurs et de l'animation
 * @param headline titre court, quelques mots
 * @param message  constat chiffre : ce qui s'est passe
 * @param advice   une seule action concrete pour la suite ; {@code null} quand il
 *                 n'y a rien d'utile a dire, ce qui vaut mieux qu'un conseil creux
 */
public record Appreciation(AppreciationTier tier, String headline, String message, String advice) {

    public static Appreciation of(AppreciationTier tier, String headline, String message) {
        return new Appreciation(tier, headline, message, null);
    }

    public Appreciation withAdvice(String newAdvice) {
        return new Appreciation(tier, headline, message, newAdvice);
    }

    /**
     * Verdict rendu faute de donnees. Le message accueille au lieu de constater
     * un vide : c'est ce que lit quelqu'un qui vient d'installer l'application.
     */
    public static Appreciation noData(String message) {
        return new Appreciation(AppreciationTier.NO_DATA, "Pas encore d'historique", message, null);
    }
}
