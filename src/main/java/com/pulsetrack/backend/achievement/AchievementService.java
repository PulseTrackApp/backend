package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.achievement.dto.AchievementResponse;
import com.pulsetrack.backend.achievement.dto.SportRecordsResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records et trophees de l'utilisateur.
 *
 * <p>Deux notions a ne pas confondre, et c'est tout le sujet de ce service :
 * <ul>
 *   <li>le <strong>record courant</strong> se recalcule a chaque lecture
 *       ({@link SportBests}), pour qu'une seance supprimee ne laisse pas derriere
 *       elle un chiffre que plus rien ne justifie ;</li>
 *   <li>le <strong>trophee</strong> ({@link WorkoutAchievement}) enregistre qu'un
 *       record est tombe tel jour. C'est un fait, il ne change plus.</li>
 * </ul>
 */
@Service
public class AchievementService {

    private final WorkoutSessionRepository sessions;
    private final WorkoutAchievementRepository achievements;
    private final AchievementDetector detector;

    public AchievementService(WorkoutSessionRepository sessions,
                              WorkoutAchievementRepository achievements,
                              AchievementDetector detector) {
        this.sessions = sessions;
        this.achievements = achievements;
        this.detector = detector;
    }

    /**
     * Meilleures performances anterieures dans un sport.
     *
     * <p>A appeler <strong>avant</strong> d'enregistrer la seance a juger : une
     * seance qui figure dans sa propre reference ne bat jamais rien.
     */
    @Transactional(readOnly = true)
    public SportBests bestsOf(UUID userId, SportType sport) {
        return SportBests.from(sessions.performancesOf(userId, sport));
    }

    public List<AchievementDetector.Detected> detect(SportBests previousBests,
                                                     AchievementDetector.Candidate candidate) {
        return detector.detect(previousBests, candidate);
    }

    public java.util.Optional<AchievementDetector.Detected> detectRouteBest(SportType sport,
                                                                           Long previousBestSeconds,
                                                                           long candidateSeconds,
                                                                           Instant achievedAt) {
        return detector.detectRouteBest(sport, previousBestSeconds, candidateSeconds, achievedAt);
    }

    /**
     * Grave les trophees d'une seance.
     *
     * <p>Appelee dans la transaction d'enregistrement de la seance : un trophee
     * annonce au client mais jamais ecrit reapparaitrait a la lecture suivante
     * comme s'il n'avait pas eu lieu.
     */
    @Transactional
    public List<AchievementResponse> award(UUID workoutId,
                                           UUID userId,
                                           List<AchievementDetector.Detected> detected) {
        if (detected.isEmpty()) {
            return List.of();
        }
        List<WorkoutAchievement> rows = detected.stream()
                .map(one -> new WorkoutAchievement(workoutId, userId, one))
                .toList();
        achievements.saveAll(rows);
        return detected.stream().map(AchievementService::toResponse).toList();
    }

    /** Trophees deja graves pour une seance, dans l'ordre ou ils sont tombes. */
    @Transactional(readOnly = true)
    public List<AchievementResponse> forWorkout(UUID workoutId) {
        return achievements.findByWorkoutIdOrderByAchievedAtAsc(workoutId).stream()
                .map(WorkoutAchievement::asDetected)
                .map(AchievementService::toResponse)
                .toList();
    }

    /**
     * Records courants, sport par sport.
     *
     * @param sport un seul sport, ou {@code null} pour tous ceux reellement
     *              pratiques — un sport sans seance n'apparait pas, une rubrique
     *              vide n'apprend rien
     */
    @Transactional(readOnly = true)
    public List<SportRecordsResponse> recordsOf(UUID userId, SportType sport) {
        List<SportType> sports = sport == null ? sessions.sportsPracticedBy(userId) : List.of(sport);

        List<SportRecordsResponse> result = new ArrayList<>();
        for (SportType practiced : sports) {
            SportBests bests = bestsOf(userId, practiced);
            if (bests.isEmpty()) {
                continue;
            }
            result.add(new SportRecordsResponse(
                    practiced,
                    bests.sessionCount(),
                    bests.firstSessionAt(),
                    recordsIn(bests)));
        }
        return List.copyOf(result);
    }

    /**
     * Ordre d'affichage : celui de declaration de l'enumeration, et non celui,
     * imprevisible, ou les records sont tombes. Un ecran dont les lignes changent
     * de place d'une visite a l'autre se lit mal.
     */
    private List<SportRecordsResponse.RecordResponse> recordsIn(SportBests bests) {
        return bests.holders().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().ordinal()))
                .map(entry -> new SportRecordsResponse.RecordResponse(
                        entry.getKey(),
                        entry.getKey().label(),
                        entry.getKey().unit(),
                        round(entry.getValue().value()),
                        entry.getValue().workoutId(),
                        entry.getValue().achievedAt()))
                .toList();
    }

    static AchievementResponse toResponse(AchievementDetector.Detected detected) {
        return new AchievementResponse(
                detected.kind(),
                detected.kind().label(),
                detected.sportType(),
                detected.kind().unit(),
                round(detected.previousValue()),
                round(detected.newValue()),
                round(detected.improvement()),
                round(detected.improvementPercent()),
                AchievementMessages.headline(detected),
                AchievementMessages.message(detected),
                detected.achievedAt());
    }

    private static Double round(Double value) {
        return value == null ? null : round(value.doubleValue());
    }

    /**
     * Une decimale suffit partout : le GPS n'en justifie pas davantage, et
     * « 6300,000000001 m » traverserait le JSON tel quel.
     */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
