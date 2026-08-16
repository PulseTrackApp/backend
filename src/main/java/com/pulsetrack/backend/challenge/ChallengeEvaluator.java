package com.pulsetrack.backend.challenge;

import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse.AlertLevel;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.motivation.Appreciation;
import com.pulsetrack.backend.motivation.AppreciationTier;
import com.pulsetrack.backend.motivation.Wording;

import org.springframework.stereotype.Component;

/**
 * Juge un defi : ou en est l'effort, et ce qu'il a finalement donne.
 *
 * <p><strong>Tolerance dissymetrique, et c'est voulu.</strong> Un pour cent de
 * marge sur la distance, parce que le GPS ne rend pas 10 000,0 metres et qu'un
 * defi refuse pour huit metres serait vecu comme une injustice. Aucune marge sur
 * le temps : une echeance qui pardonne n'est plus une echeance, et c'est
 * precisement ce qui fait tenir l'effort.
 *
 * <p>Classe sans etat ni dependance : tous les seuils s'eprouvent avec des
 * chiffres choisis, sans demarrer Spring ni enregistrer de seance.
 */
@Component
public class ChallengeEvaluator {

    /** Marge acceptee sur la distance : le GPS ne rend pas le metre exact. */
    static final double DISTANCE_TOLERANCE = 0.01;

    /** Au-dela de 10 % de temps restant, la reussite est confortable. */
    private static final double COMFORTABLE_MARGIN = 0.10;

    /**
     * Un echec est « de peu » quand la distance est presque couverte et le
     * depassement faible. C'est ce cas, et lui seul, qui merite des felicitations
     * malgre l'echec — a condition qu'un record soit tombe au passage.
     */
    private static final double NEAR_MISS_DISTANCE = 0.95;
    private static final double NEAR_MISS_TIME = 0.05;

    /** Il faut aller 15 % plus vite que jusqu'ici : l'ecart devient reel. */
    private static final double WATCH_RATIO = 1.15;

    /** Il faut aller 50 % plus vite : l'echeance est en jeu. */
    private static final double URGENT_RATIO = 1.5;

    /** Il faudrait plus que doubler d'allure : c'est perdu, autant le dire. */
    private static final double LOST_RATIO = 2.0;

    private static final double METERS_PER_KM = 1_000d;

    // -----------------------------------------------------------------------
    // Pendant l'effort
    // -----------------------------------------------------------------------

    /**
     * Point d'etape. <strong>Ne modifie rien</strong> : c'est un calcul, pas une
     * transition. Il ne peut donc pas faire echouer un defi, ce qui serait
     * desastreux si le client l'appelait au mauvais moment.
     */
    public ChallengeProgressResponse progressOf(Challenge challenge, long elapsedSeconds, double distanceMeters) {
        double targetDistance = challenge.getTargetDistanceMeters();
        long targetDuration = challenge.getTargetDurationSeconds();

        long remainingSeconds = Math.max(0, targetDuration - elapsedSeconds);
        double remainingDistance = Math.max(0, targetDistance - distanceMeters);
        double completion = targetDistance > 0 ? distanceMeters / targetDistance * 100 : 0;

        Integer currentPace = (distanceMeters > 0 && elapsedSeconds > 0)
                ? (int) Math.round(elapsedSeconds / (distanceMeters / METERS_PER_KM))
                : null;
        // Allure a tenir SUR CE QUI RESTE, et non allure moyenne du defi : c'est
        // elle qui dit quoi faire maintenant. Quelqu'un parti trop lentement doit
        // savoir a quel rythme il rattrape, pas a quel rythme il aurait fallu
        // partir.
        Integer requiredPace = (remainingDistance > 0 && remainingSeconds > 0)
                ? (int) Math.round(remainingSeconds / (remainingDistance / METERS_PER_KM))
                : null;

        double projectedDistance = elapsedSeconds > 0
                ? distanceMeters / elapsedSeconds * targetDuration
                : 0;
        Long projectedFinish = distanceMeters > 0
                ? Math.round(elapsedSeconds * (targetDistance / distanceMeters))
                : null;

        boolean done = remainingDistance <= targetDistance * DISTANCE_TOLERANCE;
        boolean onTrack = done || (projectedFinish != null && projectedFinish <= targetDuration);
        Long delta = projectedFinish == null ? null : projectedFinish - targetDuration;

        AlertLevel alert = alertLevelOf(done, onTrack, remainingSeconds, currentPace, requiredPace);

        return new ChallengeProgressResponse(
                remainingSeconds,
                round(remainingDistance),
                round(completion),
                currentPace,
                requiredPace,
                round(projectedDistance),
                projectedFinish,
                onTrack,
                delta,
                alert,
                headlineOf(alert, done),
                progressMessage(alert, done, remainingDistance, remainingSeconds, requiredPace));
    }

    private AlertLevel alertLevelOf(boolean done,
                                    boolean onTrack,
                                    long remainingSeconds,
                                    Integer currentPace,
                                    Integer requiredPace) {
        if (done || onTrack) {
            return AlertLevel.NONE;
        }
        // Le temps est ecoule et la distance n'y est pas : il n'y a plus rien a
        // rattraper. Le dire franchement vaut mieux qu'un encouragement absurde.
        if (remainingSeconds <= 0) {
            return AlertLevel.LOST;
        }
        // Toujours a l'arret alors que le temps court : on avertit, sans pouvoir
        // calculer de rapport d'allure faute d'allure courante.
        if (currentPace == null || requiredPace == null) {
            return AlertLevel.WATCH;
        }

        // Facteur d'acceleration necessaire : 1,3 veut dire « il faut aller 30 %
        // plus vite que depuis le depart ».
        double ratio = currentPace / (double) requiredPace;
        if (ratio <= WATCH_RATIO) {
            return AlertLevel.WATCH;
        }
        if (ratio <= URGENT_RATIO) {
            return AlertLevel.URGENT;
        }
        return ratio >= LOST_RATIO ? AlertLevel.LOST : AlertLevel.URGENT;
    }

    private String headlineOf(AlertLevel alert, boolean done) {
        if (done) {
            return "Distance couverte";
        }
        return switch (alert) {
            case NONE -> "Dans les temps";
            case WATCH -> "Tu perds un peu de terrain";
            case URGENT -> "L'échéance approche";
            case LOST -> "L'échéance ne sera pas tenue";
        };
    }

    private String progressMessage(AlertLevel alert,
                                   boolean done,
                                   double remainingDistance,
                                   long remainingSeconds,
                                   Integer requiredPace) {
        if (done) {
            return "La distance est faite. Termine comme tu le sens.";
        }
        String reste = "Il te reste %s en %s".formatted(
                Wording.distance(remainingDistance), Wording.duration(remainingSeconds));

        return switch (alert) {
            case NONE -> reste + " : garde ce rythme et c'est plié.";
            case WATCH -> requiredPace == null
                    ? reste + ". Il est temps de relancer."
                    : reste + " : il faut passer à " + Wording.pace(requiredPace) + ".";
            case URGENT -> requiredPace == null
                    ? reste + ". C'est maintenant ou jamais."
                    : reste + " : " + Wording.pace(requiredPace) + " pour tenir. Tout donner.";
            // Ni faux espoir, ni reproche : on propose une sortie honorable.
            case LOST -> reste + ". Le chronomètre ne passera pas, mais la distance, si. Va la chercher.";
        };
    }

    // -----------------------------------------------------------------------
    // Apres l'effort
    // -----------------------------------------------------------------------

    /**
     * Verdict final.
     *
     * @param recordBeaten un record est tombe pendant cette seance. C'est ce qui
     *                     transforme un echec de justesse en moment a feter :
     *                     manquer un defi de dix secondes sur sa meilleure sortie
     *                     de l'annee merite mieux qu'un ecran rouge
     */
    public ChallengeResponse.Result evaluate(Challenge challenge,
                                             double distanceMeters,
                                             long durationSeconds,
                                             boolean recordBeaten) {
        double targetDistance = challenge.getTargetDistanceMeters();
        long targetDuration = challenge.getTargetDurationSeconds();

        boolean distanceCovered = distanceMeters >= targetDistance * (1 - DISTANCE_TOLERANCE);
        boolean inTime = durationSeconds <= targetDuration;
        boolean succeeded = distanceCovered && inTime;

        double distanceMargin = distanceMeters - targetDistance;
        long timeMargin = targetDuration - durationSeconds;
        double completion = targetDistance > 0 ? distanceMeters / targetDistance * 100 : 0;
        Integer achievedPace = distanceMeters > 0
                ? (int) Math.round(durationSeconds / (distanceMeters / METERS_PER_KM))
                : null;

        boolean nearMiss = !succeeded
                && distanceMeters >= targetDistance * NEAR_MISS_DISTANCE
                && durationSeconds <= targetDuration * (1 + NEAR_MISS_TIME);

        return new ChallengeResponse.Result(
                succeeded,
                round(distanceMeters),
                durationSeconds,
                round(distanceMargin),
                timeMargin,
                round(completion),
                achievedPace,
                succeeded || (nearMiss && recordBeaten),
                appreciationOf(challenge, succeeded, nearMiss, distanceCovered,
                        distanceMeters, durationSeconds, timeMargin, achievedPace));
    }

    private Appreciation appreciationOf(Challenge challenge,
                                        boolean succeeded,
                                        boolean nearMiss,
                                        boolean distanceCovered,
                                        double distanceMeters,
                                        long durationSeconds,
                                        long timeMargin,
                                        Integer achievedPace) {

        long targetDuration = challenge.getTargetDurationSeconds();
        String fait = "%s en %s".formatted(Wording.distance(distanceMeters), Wording.duration(durationSeconds));

        if (succeeded) {
            boolean comfortable = timeMargin >= targetDuration * COMFORTABLE_MARGIN;
            String message = timeMargin == 0
                    ? "%s : pile à l'échéance.".formatted(fait)
                    : "%s : %s de marge sur l'échéance.".formatted(fait, Wording.duration(timeMargin));
            return new Appreciation(
                    AppreciationTier.EXCELLENT,
                    comfortable ? "Défi relevé haut la main" : "Défi relevé",
                    message,
                    nextStepAdvice(challenge, comfortable, achievedPace));
        }

        if (nearMiss) {
            return new Appreciation(
                    AppreciationTier.BEHIND,
                    "À quelques secondes près",
                    "%s, pour un objectif de %s en %s. Il ne manquait presque rien."
                            .formatted(fait, Wording.distance(challenge.getTargetDistanceMeters()),
                                    Wording.duration(targetDuration)),
                    "Retente le même défi : à cet écart, il tombe à la prochaine sortie.");
        }

        if (!distanceCovered) {
            return new Appreciation(
                    AppreciationTier.BEHIND,
                    "Distance non couverte",
                    "%s sur les %s visés. La sortie compte quand même."
                            .formatted(fait, Wording.distance(challenge.getTargetDistanceMeters())),
                    "Vise d'abord la distance sans chronomètre, le temps viendra ensuite.");
        }

        return new Appreciation(
                AppreciationTier.BEHIND,
                "Distance faite, échéance dépassée",
                "%s, soit %s de plus que l'échéance. La distance, elle, est bien couverte."
                        .formatted(fait, Wording.duration(durationSeconds - targetDuration)),
                "Reprends le même défi avec %s de plus au compteur : le passage se fera."
                        .formatted(Wording.duration(durationSeconds - targetDuration)));
    }

    /**
     * Une seule action concrete pour la suite. Un conseil unique se suit ; trois
     * se lisent et s'oublient.
     */
    private String nextStepAdvice(Challenge challenge, boolean comfortable, Integer achievedPace) {
        if (!comfortable || achievedPace == null) {
            return null;
        }
        // Le cran suivant : cinq pour cent de temps en moins sur la meme
        // distance. Assez pour que ce soit un vrai defi, assez peu pour rester
        // atteignable a la prochaine sortie.
        long harder = Math.round(challenge.getTargetDurationSeconds() * 0.95);
        return "Le prochain cran serait %s en %s."
                .formatted(Wording.distance(challenge.getTargetDistanceMeters()), Wording.duration(harder));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
