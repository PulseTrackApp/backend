package com.pulsetrack.backend.challenge.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.challenge.ChallengeStatus;
import com.pulsetrack.backend.challenge.DifficultyLevel;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.motivation.Appreciation;

/**
 * Un defi, dans l'etat ou il se trouve.
 *
 * @param deadlineAt fin du chronometre, {@code startedAt} plus la duree cible.
 *                   Nulle tant que le defi n'est pas arme. A ne pas confondre
 *                   avec {@code expiresOn}, date limite pour <em>tenter</em> le
 *                   defi
 * @param difficulty avis rendu <strong>avant</strong> l'effort : le defi est-il
 *                   bien calibre pour cette personne
 * @param plan       tableau de marche ; rempli seulement quand le defi est arme
 * @param result     verdict ; rempli seulement une fois le defi joue
 */
public record ChallengeResponse(
        UUID id,
        String title,
        SportType sportType,
        double targetDistanceMeters,
        long targetDurationSeconds,
        int requiredPaceSecondsPerKm,
        double requiredSpeedKmh,
        UUID routeId,
        String routeName,
        ChallengeStatus status,
        LocalDate expiresOn,
        Instant createdAt,
        Instant startedAt,
        Instant deadlineAt,
        Instant completedAt,
        UUID workoutId,
        Difficulty difficulty,
        ChallengePlanResponse plan,
        Result result) {

    /**
     * @param referencePaceSecondsPerKm allure de comparaison ; {@code null} quand
     *                                  l'historique ne permet pas d'en tirer une
     */
    public record Difficulty(
            DifficultyLevel level,
            String headline,
            String message,
            Integer referencePaceSecondsPerKm,
            DifficultyLevel.ReferenceBasis referenceBasis) {
    }

    /**
     * Ce que le defi a donne.
     *
     * @param distanceMarginMeters marge sur la distance ; negative si elle n'a
     *                             pas ete couverte
     * @param timeMarginSeconds    marge sur l'echeance ; negative en cas de
     *                             depassement
     * @param celebrate            vrai s'il faut feliciter. Vrai en cas de
     *                             reussite, et aussi quand l'echec est de peu
     *                             tout en battant un record : un defi manque de
     *                             dix secondes sur la meilleure sortie de l'annee
     *                             merite mieux qu'un ecran rouge
     */
    public record Result(
            boolean succeeded,
            double achievedDistanceMeters,
            long achievedDurationSeconds,
            double distanceMarginMeters,
            long timeMarginSeconds,
            double completionPercent,
            Integer achievedPaceSecondsPerKm,
            boolean celebrate,
            Appreciation appreciation) {
    }
}
