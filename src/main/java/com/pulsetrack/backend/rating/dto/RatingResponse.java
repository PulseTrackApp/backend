package com.pulsetrack.backend.rating.dto;

import java.time.LocalDate;
import java.util.List;

import com.pulsetrack.backend.rating.RatingComponent;
import com.pulsetrack.backend.rating.RatingTier;

/**
 * Note de l'utilisateur et encouragement qui va avec.
 *
 * <p>Calculee sur une fenetre glissante de vingt-huit jours, de facon
 * <strong>deterministe</strong> : aucun appel a l'assistant, aucune latence,
 * aucun quota, et deux appels le meme jour rendent la meme note.
 *
 * <p><strong>Un compte sans seance ne recoit pas zero.</strong> Il recoit
 * {@link RatingTier#NEW}, {@code score} et {@code grade} a {@code null}, et un
 * message d'accueil. Le client doit traiter ce cas a part : afficher « 0/100 » a
 * quelqu'un qui vient d'installer l'application est le plus sur moyen de le
 * perdre.
 *
 * @param score            0 a 100 ; {@code null} sans aucune seance
 * @param grade            {@code A+} a {@code E} ; {@code null} sans seance
 * @param title            titre du palier, « Regulier » ; {@code null} sans seance
 * @param message          encouragement chiffre, redige cote serveur
 * @param advice           une seule action concrete pour la suite
 * @param windowDays       largeur de la fenetre d'analyse
 * @param computedFor      jour de reference, dans le fuseau demande
 * @param streakDays       jours consecutifs avec au moins une seance
 * @param pointsToNextTier points manquants pour le palier suivant ; {@code null}
 *                         au sommet de l'echelle
 * @param components       le detail de la note, pour l'ecran qui explique. Les
 *                         poids somment a 100
 */
public record RatingResponse(
        Integer score,
        String grade,
        RatingTier tier,
        String title,
        String message,
        String advice,
        int windowDays,
        LocalDate computedFor,
        int streakDays,
        RatingTier nextTier,
        Integer pointsToNextTier,
        Trend trend,
        List<Component> components) {

    /**
     * Comparaison avec les vingt-huit jours precedents.
     *
     * @param previousScore {@code null} quand il n'y avait rien avant
     */
    public record Trend(Integer previousScore, Integer delta, Direction direction) {

        /** Constante de protocole : le client choisit la fleche et la couleur. */
        public enum Direction {
            UP,
            FLAT,
            DOWN
        }
    }

    /**
     * @param comment ce qui justifie la note de cette composante, en une phrase.
     *                Une note sans motif se conteste
     */
    public record Component(
            RatingComponent key,
            String label,
            int score,
            int weight,
            String comment) {
    }
}
