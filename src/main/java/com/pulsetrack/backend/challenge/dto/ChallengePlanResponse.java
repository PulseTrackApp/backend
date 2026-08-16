package com.pulsetrack.backend.challenge.dto;

import java.util.List;

/**
 * Le tableau de marche remis au depart d'un defi.
 *
 * <p><strong>Fait pour etre joue hors ligne.</strong> Le client le recoit une
 * fois, en armant le defi, puis declenche chaque {@link Cue} localement quand son
 * seuil est franchi. Il ne doit <em>pas</em> appeler le serveur pendant la
 * course : le reseau est mauvais en mouvement, et une alerte d'echeance qui
 * attend une reponse HTTP arrive apres l'echeance.
 *
 * @param splits tableau de marche, un point par kilometre — par demi-kilometre
 *               sous trois kilometres, ou un jalon unique n'apprendrait rien
 */
public record ChallengePlanResponse(
        int requiredPaceSecondsPerKm,
        double requiredSpeedKmh,
        List<Split> splits,
        List<Cue> cues) {

    /**
     * @param distanceMeters       distance cumulee au jalon
     * @param targetElapsedSeconds temps auquel il faut y etre
     * @param label                libelle pret a afficher, « km 3 »
     */
    public record Split(int index, double distanceMeters, long targetElapsedSeconds, String label) {
    }

    /**
     * Un message a jouer quand une condition est franchie, <strong>une seule fois
     * chacun</strong>.
     *
     * @param trigger   ce qu'il faut surveiller
     * @param threshold seuil, dans l'unite du declencheur
     */
    public record Cue(CueTrigger trigger, double threshold, CueKind kind, String title, String message) {
    }

    /**
     * Ce que le client surveille. Constante de protocole.
     *
     * <p>Deux se franchissent a la hausse, deux a la baisse : c'est ecrit dans
     * chaque valeur, ne pas le deviner.
     */
    public enum CueTrigger {

        /** Part du temps imparti ecoulee, en pourcentage. Franchi a la hausse. */
        ELAPSED_PERCENT,

        /** Part de la distance couverte, en pourcentage. Franchi a la hausse. */
        DISTANCE_PERCENT,

        /** Secondes restantes avant l'echeance. Franchi <strong>a la baisse</strong>. */
        REMAINING_SECONDS,

        /** Metres restants a couvrir. Franchi <strong>a la baisse</strong>. */
        DISTANCE_REMAINING_METERS
    }

    /**
     * Nature du message, pour choisir le ton, le son et la vibration. Une alerte
     * d'echeance merite qu'on la sente ; un encouragement, non.
     */
    public enum CueKind {
        MOTIVATION,
        PACE_WARNING,
        DEADLINE_ALERT,
        FINAL_PUSH
    }
}
