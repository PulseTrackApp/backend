package com.pulsetrack.backend.challenge.dto;

/**
 * Ou en est l'effort, et ce qu'il faut en penser a cet instant.
 *
 * @param currentPaceSecondsPerKm   allure tenue jusqu'ici ; {@code null} tant
 *                                  qu'aucune distance n'est couverte
 * @param requiredPaceSecondsPerKm  allure a tenir <strong>sur ce qui reste</strong>,
 *                                  et non l'allure moyenne du defi : c'est
 *                                  celle-la qui dit quoi faire maintenant.
 *                                  {@code null} si la distance est deja couverte
 * @param projectedDistanceMeters   ou l'on arrivera au rythme actuel
 * @param projectedFinishSeconds    temps auquel la distance sera couverte au
 *                                  rythme actuel ; {@code null} a l'arret
 * @param deltaSeconds              retard sur l'echeance, en secondes ; negatif
 *                                  quand on est en avance
 */
public record ChallengeProgressResponse(
        long remainingSeconds,
        double remainingDistanceMeters,
        double completionPercent,
        Integer currentPaceSecondsPerKm,
        Integer requiredPaceSecondsPerKm,
        double projectedDistanceMeters,
        Long projectedFinishSeconds,
        boolean onTrack,
        Long deltaSeconds,
        AlertLevel alertLevel,
        String headline,
        String message) {

    /**
     * Urgence de la situation. Constante de protocole : le client choisit
     * couleur, son et vibration la-dessus.
     */
    public enum AlertLevel {

        /** Dans les temps, rien a signaler. */
        NONE,

        /** Du retard, mais l'ecart se comble. */
        WATCH,

        /** L'echeance approche et l'ecart est reel. */
        URGENT,

        /**
         * L'objectif n'est plus atteignable, meme a pleine vitesse. Le message
         * reste digne : il propose de finir la distance sans le chronometre.
         */
        LOST
    }
}
