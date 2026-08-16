package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pulsetrack.backend.common.domain.SportType;

import org.springframework.stereotype.Component;

/**
 * Decide quels records une seance vient de faire tomber.
 *
 * <p>Classe sans etat, sans dependance et sans acces a la base : elle recoit les
 * meilleures performances anterieures et la seance a juger, et rend le verdict.
 * C'est ce qui permet d'eprouver les seuils anti-bruit en une milliseconde, avec
 * des chiffres choisis, sans demarrer Spring ni enregistrer de seance.
 */
@Component
public class AchievementDetector {

    /**
     * Seance soumise au jugement, reduite a ce qui peut faire un record.
     *
     * @param averagePaceSecondsPerKm {@code null} quand la seance n'a pas de
     *                                distance mesurable
     */
    public record Candidate(
            SportType sportType,
            double distanceMeters,
            long movingDurationSeconds,
            Integer averagePaceSecondsPerKm,
            double elevationGainMeters,
            Instant achievedAt) {
    }

    /**
     * Un record tombe.
     *
     * @param previousValue valeur qui vient d'etre depassee ; {@code null} pour
     *                      un premier evenement, qui n'a pas de precedent
     */
    public record Detected(
            AchievementKind kind,
            SportType sportType,
            Double previousValue,
            double newValue,
            Instant achievedAt) {

        /** Gain, toujours positif ; {@code null} sans valeur precedente. */
        public Double improvement() {
            return previousValue == null ? null : kind.improvementOver(previousValue, newValue);
        }

        /**
         * Gain rapporte au record precedent. {@code null} sans precedent, et
         * aussi quand celui-ci valait zero : diviser par zero rendrait un
         * infini que le client afficherait tel quel.
         */
        public Double improvementPercent() {
            if (previousValue == null || previousValue == 0d) {
                return null;
            }
            return kind.improvementOver(previousValue, newValue) / Math.abs(previousValue) * 100;
        }
    }

    /**
     * @param previousBests meilleures performances du sport <strong>avant</strong>
     *                      cette seance. La seance jugee ne doit pas en faire
     *                      partie, sinon elle se comparerait a elle-meme et
     *                      aucun record ne tomberait jamais.
     * @return les records tombes, dans l'ordre ou ils meritent d'etre annonces ;
     *         liste vide si la seance n'a rien battu, ce qui est le cas courant
     */
    public List<Detected> detect(SportBests previousBests, Candidate candidate) {
        // Toute premiere seance du sport : on celebre l'evenement et on s'arrete
        // la. Annoncer quatre records d'un coup a quelqu'un qui vient de courir
        // pour la premiere fois sonne faux — il n'a rien battu, il a commence.
        if (previousBests.isEmpty()) {
            return List.of(new Detected(
                    AchievementKind.FIRST_SESSION, candidate.sportType(), null, 1d, candidate.achievedAt()));
        }

        List<Detected> detected = new ArrayList<>();

        if (candidate.distanceMeters() > 0) {
            record(detected, AchievementKind.LONGEST_DISTANCE, previousBests, candidate,
                    candidate.distanceMeters());
        }
        if (candidate.movingDurationSeconds() > 0) {
            record(detected, AchievementKind.LONGEST_MOVING_DURATION, previousBests, candidate,
                    candidate.movingDurationSeconds());
        }
        // L'allure n'est jugee qu'au-dela du kilometre. En dessous, la moindre
        // imprecision de trace change le chiffre du tout au tout.
        if (candidate.averagePaceSecondsPerKm() != null
                && candidate.distanceMeters() >= AchievementKind.MIN_DISTANCE_FOR_PACE_METERS) {
            record(detected, AchievementKind.BEST_AVERAGE_PACE, previousBests, candidate,
                    candidate.averagePaceSecondsPerKm());
        }
        if (candidate.elevationGainMeters() > 0) {
            record(detected, AchievementKind.HIGHEST_ELEVATION_GAIN, previousBests, candidate,
                    candidate.elevationGainMeters());
        }

        return List.copyOf(detected);
    }

    /**
     * Meilleur temps sur un parcours rejoue.
     *
     * <p>Traite a part des autres records : il ne se compare pas a l'historique
     * du sport mais aux seules tentatives sur ce circuit, et il n'existe que
     * lorsque l'utilisateur a explicitement declare rejouer un parcours.
     *
     * @param previousBestSeconds meilleur temps des tentatives precedentes ;
     *                            {@code null} au premier passage
     */
    public Optional<Detected> detectRouteBest(SportType sportType,
                                              Long previousBestSeconds,
                                              long candidateSeconds,
                                              Instant achievedAt) {
        if (candidateSeconds <= 0) {
            return Optional.empty();
        }
        // Premier passage sur le circuit : ce n'est pas un temps battu, et
        // l'annoncer comme un record serait mentir. La comparaison de parcours
        // le signale deja comme le meilleur temps par defaut.
        if (previousBestSeconds == null) {
            return Optional.empty();
        }
        if (!AchievementKind.BEST_ROUTE_TIME.beats(previousBestSeconds.doubleValue(), candidateSeconds)) {
            return Optional.empty();
        }
        return Optional.of(new Detected(AchievementKind.BEST_ROUTE_TIME, sportType,
                previousBestSeconds.doubleValue(), candidateSeconds, achievedAt));
    }

    private void record(List<Detected> into,
                        AchievementKind kind,
                        SportBests previousBests,
                        Candidate candidate,
                        double value) {
        Double previous = previousBests.valueOf(kind);
        // Aucune valeur anterieure alors que le sport a deja des seances : c'est
        // le premier releve mesurable de cette categorie. Il fait record, mais
        // sans precedent a afficher.
        if (kind.beats(previous, value)) {
            into.add(new Detected(kind, candidate.sportType(), previous, value, candidate.achievedAt()));
        }
    }
}
