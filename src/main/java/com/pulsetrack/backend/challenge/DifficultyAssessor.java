package com.pulsetrack.backend.challenge;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

import com.pulsetrack.backend.achievement.AchievementKind;
import com.pulsetrack.backend.achievement.SportPerformanceRow;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.motivation.Wording;

import org.springframework.stereotype.Component;

/**
 * Dit si un defi est bien calibre, avant qu'il soit tente.
 *
 * <p>C'est l'appreciation qui manque le plus a qui se fixe un chiffre au hasard :
 * « 10 km en 50 minutes », est-ce ambitieux ou hors sujet ? Personne ne peut
 * repondre seul, le serveur le sait — il a l'historique.
 *
 * <p><strong>On n'interdit jamais.</strong> Meme
 * {@link DifficultyLevel#HORS_DE_PORTEE} laisse creer le defi : quelqu'un a le
 * droit de viser trop haut, et un refus serait pris pour un jugement.
 *
 * <p>Classe sans etat ni acces a la base : elle recoit l'historique et rend un
 * verdict, ce qui la rend eprouvable avec des chiffres choisis.
 */
@Component
public class DifficultyAssessor {

    /**
     * Sous trois seances dans le sport, la moyenne ne veut rien dire : une seule
     * sortie d'essai suffirait a la fausser du tout au tout.
     */
    static final int MIN_SESSIONS_FOR_JUDGEMENT = 3;

    /** Nombre de seances recentes prises pour reference. */
    static final int REFERENCE_WINDOW = 10;

    /** Jusqu'a 5 % plus rapide que l'habitude : realiste. */
    static final double REALISTIC_SPEEDUP = 0.05;

    /** Jusqu'a 15 % : ambitieux, le cran qui fait progresser. */
    static final double AMBITIOUS_SPEEDUP = 0.15;

    /**
     * @param history seances du sport, avec leur allure. Celles qui n'en ont pas,
     *                ou qui font moins d'un kilometre, sont ecartees : une allure
     *                mesuree sur deux cents metres est du bruit
     */
    public ChallengeResponse.Difficulty assess(SportType sport,
                                               int requiredPaceSecondsPerKm,
                                               List<SportPerformanceRow> history) {

        List<SportPerformanceRow> usable = history.stream()
                .filter(row -> row.averagePaceSecondsPerKm() != null)
                .filter(row -> row.distanceMeters() >= AchievementKind.MIN_DISTANCE_FOR_PACE_METERS)
                .sorted(Comparator.comparing(SportPerformanceRow::startedAt).reversed())
                .toList();

        if (usable.size() < MIN_SESSIONS_FOR_JUDGEMENT) {
            return new ChallengeResponse.Difficulty(
                    DifficultyLevel.INCONNU,
                    "Premier reperage",
                    "Pas encore assez de seances en %s pour situer ce defi. Il servira de reference."
                            .formatted(sport.label()),
                    null,
                    DifficultyLevel.ReferenceBasis.NONE);
        }

        OptionalDouble average = usable.stream()
                .limit(REFERENCE_WINDOW)
                .mapToInt(SportPerformanceRow::averagePaceSecondsPerKm)
                .average();
        int averagePace = (int) Math.round(average.orElseThrow());
        int bestPace = usable.stream()
                .mapToInt(SportPerformanceRow::averagePaceSecondsPerKm)
                .min()
                .orElseThrow();

        // Fraction de vitesse a gagner sur l'habitude. Positive quand le defi
        // demande d'aller plus vite, ce qui est le cas interessant.
        double speedup = (averagePace - requiredPaceSecondsPerKm) / (double) averagePace;

        if (speedup <= 0) {
            return difficulty(DifficultyLevel.ACCESSIBLE, averagePace,
                    DifficultyLevel.ReferenceBasis.AVERAGE_LAST_10,
                    "Largement a ta portee",
                    "Ton allure moyenne en %s est de %s. Ce defi en demande %s : tu as de la marge."
                            .formatted(sport.label(), Wording.pace(averagePace),
                                    Wording.pace(requiredPaceSecondsPerKm)));
        }

        if (speedup <= REALISTIC_SPEEDUP) {
            return difficulty(DifficultyLevel.REALISTE, averagePace,
                    DifficultyLevel.ReferenceBasis.AVERAGE_LAST_10,
                    "A ta portee",
                    "Un peu au-dessus de ton allure habituelle de %s. C'est jouable."
                            .formatted(Wording.pace(averagePace)));
        }

        if (speedup <= AMBITIOUS_SPEEDUP) {
            return difficulty(DifficultyLevel.AMBITIEUX, averagePace,
                    DifficultyLevel.ReferenceBasis.AVERAGE_LAST_10,
                    "Un cran au-dessus de ton habitude",
                    "Ton allure moyenne en %s est de %s. Ce defi en demande %s : c'est le bon ecart pour progresser."
                            .formatted(sport.label(), Wording.pace(averagePace),
                                    Wording.pace(requiredPaceSecondsPerKm)));
        }

        // Loin de la moyenne, mais deja fait au moins une fois : ce n'est pas
        // hors de portee, c'est un retour au sommet.
        if (requiredPaceSecondsPerKm >= bestPace) {
            return difficulty(DifficultyLevel.AMBITIEUX, bestPace,
                    DifficultyLevel.ReferenceBasis.BEST_EVER,
                    "Le niveau de ton meilleur jour",
                    "Bien au-dessus de ton habitude, mais tu as deja tenu %s en %s. C'est un retour au sommet."
                            .formatted(Wording.pace(bestPace), sport.label()));
        }

        return difficulty(DifficultyLevel.HORS_DE_PORTEE, bestPace,
                DifficultyLevel.ReferenceBasis.BEST_EVER,
                "Tres au-dessus de ce que tu as deja fait",
                "Ta meilleure allure en %s est de %s, ce defi en demande %s. Rien ne t'empeche d'essayer, mais vise plutot un premier palier."
                        .formatted(sport.label(), Wording.pace(bestPace),
                                Wording.pace(requiredPaceSecondsPerKm)));
    }

    private ChallengeResponse.Difficulty difficulty(DifficultyLevel level,
                                                    Integer referencePace,
                                                    DifficultyLevel.ReferenceBasis basis,
                                                    String headline,
                                                    String message) {
        return new ChallengeResponse.Difficulty(level, headline, message, referencePace, basis);
    }
}
