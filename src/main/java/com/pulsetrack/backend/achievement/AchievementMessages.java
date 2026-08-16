package com.pulsetrack.backend.achievement;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.motivation.Wording;

/**
 * Redige les felicitations qui accompagnent un record.
 *
 * <p>Le texte est compose ici, avec les chiffres, et non par le client : une
 * phrase ecrite d'un cote et des valeurs formatees de l'autre finissent toujours
 * par ne plus s'accorder. C'est aussi la garantie qu'Android et iOS felicitent
 * avec les memes mots.
 *
 * <p>Aucun appel a l'assistant : une felicitation ne doit rien couter, ne
 * dependre d'aucune cle tierce, et surtout ne pas faire patienter deux secondes
 * quelqu'un qui vient de franchir sa ligne d'arrivee.
 */
final class AchievementMessages {

    private AchievementMessages() {
    }

    /** Titre court, quelques mots, destine a la banniere de celebration. */
    static String headline(AchievementDetector.Detected detected) {
        return switch (detected.kind()) {
            case FIRST_SESSION -> "Premiere seance enregistree";
            case LONGEST_DISTANCE -> "Nouveau record de distance";
            case LONGEST_MOVING_DURATION -> "Nouveau record d'endurance";
            case BEST_AVERAGE_PACE -> "Nouveau record d'allure";
            case HIGHEST_ELEVATION_GAIN -> "Nouveau record de denivele";
            case BEST_ROUTE_TIME -> "Nouveau meilleur temps";
        };
    }

    /**
     * Le constat chiffre. Il dit toujours <em>de combien</em> le record est
     * battu : « plus loin que jamais » n'apprend rien, « 1,18 km de plus »
     * donne la mesure de l'effort.
     */
    static String message(AchievementDetector.Detected detected) {
        SportType sport = detected.sportType();
        Double previous = detected.previousValue();

        return switch (detected.kind()) {
            case FIRST_SESSION -> "Ta premiere seance de %s est enregistree. Tout part de la."
                    .formatted(sport.label());

            case LONGEST_DISTANCE -> previous == null
                    ? "%s : ta plus longue sortie en %s.".formatted(
                            Wording.distance(detected.newValue()), sport.label())
                    : "%s, soit %s de plus que ton precedent record en %s.".formatted(
                            Wording.distance(detected.newValue()),
                            Wording.distance(detected.newValue() - previous),
                            sport.label());

            case LONGEST_MOVING_DURATION -> previous == null
                    ? "%s en mouvement : ta plus longue seance de %s.".formatted(
                            Wording.duration((long) detected.newValue()), sport.label())
                    : "%s en mouvement, soit %s de plus que ton precedent record.".formatted(
                            Wording.duration((long) detected.newValue()),
                            Wording.duration((long) (detected.newValue() - previous)));

            case BEST_AVERAGE_PACE -> previous == null
                    ? "%s : ta meilleure allure en %s.".formatted(
                            Wording.pace((int) detected.newValue()), sport.label())
                    : "%s, soit %s gagnees au kilometre sur ton precedent record.".formatted(
                            Wording.pace((int) detected.newValue()),
                            Wording.duration((long) (previous - detected.newValue())));

            case HIGHEST_ELEVATION_GAIN -> previous == null
                    ? "%d m de denivele positif : ton plus gros en %s.".formatted(
                            Math.round(detected.newValue()), sport.label())
                    : "%d m de denivele positif, soit %d m de plus que ton precedent record.".formatted(
                            Math.round(detected.newValue()),
                            Math.round(detected.newValue() - previous));

            case BEST_ROUTE_TIME -> "%s sur ce parcours, soit %s de gagnees sur ton meilleur temps."
                    .formatted(
                            Wording.duration((long) detected.newValue()),
                            Wording.duration((long) (previous - detected.newValue())));
        };
    }
}
