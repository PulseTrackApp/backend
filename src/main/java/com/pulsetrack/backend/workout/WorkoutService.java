package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.achievement.AchievementDetector;
import com.pulsetrack.backend.achievement.AchievementService;
import com.pulsetrack.backend.achievement.SportBests;
import com.pulsetrack.backend.achievement.dto.AchievementResponse;
import com.pulsetrack.backend.challenge.ChallengeService;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.profile.ProfileService;
import com.pulsetrack.backend.route.RouteService;
import com.pulsetrack.backend.route.dto.RouteComparisonResponse;
import com.pulsetrack.backend.workout.dto.CreateWorkoutRequest;
import com.pulsetrack.backend.workout.dto.GpsPointRequest;
import com.pulsetrack.backend.workout.dto.GpsPointResponse;
import com.pulsetrack.backend.workout.dto.WorkoutResponse;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistrement et consultation des seances.
 *
 * <p>Point d'attention transversal : chaque methode recoit l'identifiant de
 * l'utilisateur courant et le transmet au repository. Aucune lecture ne part de
 * l'identifiant de seance seul.
 */
@Service
public class WorkoutService {

    private final WorkoutSessionRepository sessions;
    private final WorkoutMetricsCalculator calculator;
    private final ProfileService profileService;
    private final AchievementService achievementService;
    private final RouteService routeService;
    private final ChallengeService challengeService;

    public WorkoutService(WorkoutSessionRepository sessions,
                          WorkoutMetricsCalculator calculator,
                          ProfileService profileService,
                          AchievementService achievementService,
                          RouteService routeService,
                          ChallengeService challengeService) {
        this.sessions = sessions;
        this.calculator = calculator;
        this.profileService = profileService;
        this.achievementService = achievementService;
        this.routeService = routeService;
        this.challengeService = challengeService;
    }

    /**
     * Calcule les metriques puis persiste la seance et son trace.
     *
     * @throws BusinessRuleException     si la fenetre de temps est incoherente
     * @throws ResourceNotFoundException si le profil (donc le poids) manque
     */
    @Transactional
    public Recorded create(UUID userId, CreateWorkoutRequest request) {
        if (!request.endedAt().isAfter(request.startedAt())) {
            throw new BusinessRuleException("La fin de séance doit être postérieure au début.");
        }

        UUID id = request.id();
        if (id != null) {
            // Renvoi de la meme seance : on rend celle deja enregistree plutot
            // que d'en creer une seconde. C'est ce qui rend l'envoi rejouable
            // apres une coupure reseau en fin de course. Les trophees sont relus
            // en base, donc identiques : les felicitations n'explosent pas deux
            // fois, et ne se perdent pas si le premier envoi n'a jamais atteint
            // l'ecran.
            Optional<WorkoutSession> already = sessions.findByIdAndUserId(id, userId);
            if (already.isPresent()) {
                return new Recorded(toDetail(already.get()), false);
            }
            // Identifiant deja pris par quelqu'un d'autre : refuser proprement,
            // sinon l'insertion viole la cle primaire et remonte en 500. Le
            // message ne dit pas a qui il appartient.
            if (sessions.existsById(id)) {
                throw new ConflictException("Cet identifiant de séance est déjà utilisé.");
            }
        }

        // Ordonner le trace avant tout calcul : rien ne garantit que le mobile
        // ait envoye les points dans l'ordre, et un segment a rebours produirait
        // une duree negative.
        List<GpsPointRequest> track = sortedTrack(request.gpsPoints());

        double weightKg = profileService.weightKgOf(userId);
        WorkoutMetrics metrics = calculator.calculate(
                request.sportType(),
                request.startedAt(),
                request.endedAt(),
                track,
                request.distanceMeters(),
                weightKg);

        // Un parcours inconnu est refuse avant tout enregistrement : mieux vaut
        // une erreur claire qu'une seance rattachee dans le vide, qui ne
        // remonterait dans aucun classement sans que personne comprenne pourquoi.
        if (request.routeId() != null) {
            routeService.requireOwned(userId, request.routeId());
        }

        // Les records d'AVANT cette seance, lus avant de l'enregistrer : une
        // seance qui figure dans sa propre reference ne bat jamais rien.
        SportBests previousBests = achievementService.bestsOf(userId, request.sportType());

        WorkoutSession session = new WorkoutSession(
                id != null ? id : UUID.randomUUID(),
                userId,
                request.sportType(),
                request.startedAt(),
                request.endedAt(),
                metrics,
                request.perceivedEffort(),
                request.feeling(),
                normalizeNote(request.note()),
                Instant.now());
        session.attachTo(request.routeId(), request.challengeId());

        for (int position = 0; position < track.size(); position++) {
            GpsPointRequest point = track.get(position);
            session.addGpsPoint(
                    position,
                    point.latitude(),
                    point.longitude(),
                    point.altitude(),
                    point.accuracy(),
                    point.speed(),
                    point.recordedAt());
        }

        // Le meilleur temps du parcours doit etre lu avant l'enregistrement, lui
        // aussi : une fois la seance ecrite, elle serait sa propre reference.
        Long previousRouteBest = request.routeId() == null
                ? null
                : routeService.bestDurationExcluding(userId, request.routeId(), session.getId());

        WorkoutSession saved = sessions.save(session);

        return new Recorded(toDetail(saved, previousBests, previousRouteBest, request.challengeId()), true);
    }

    /**
     * Assemble ce que la sortie a change : records tombes, place sur le parcours,
     * verdict du defi.
     *
     * <p>L'ordre compte. Les trophees sont graves d'abord, parce que le defi a
     * besoin de savoir si un record est tombe : c'est ce qui transforme un echec
     * de dix secondes en moment a feter plutot qu'en ecran rouge.
     */
    private WorkoutResponse toDetail(WorkoutSession saved,
                                     SportBests previousBests,
                                     Long previousRouteBest,
                                     UUID challengeId) {

        List<AchievementDetector.Detected> detected = new ArrayList<>(achievementService.detect(
                previousBests,
                new AchievementDetector.Candidate(
                        saved.getSportType(),
                        saved.getDistanceMeters(),
                        saved.getMovingDurationSeconds(),
                        saved.getAveragePaceSecondsPerKm(),
                        saved.getElevationGainMeters(),
                        saved.getStartedAt())));

        if (saved.getRouteId() != null) {
            achievementService.detectRouteBest(
                            saved.getSportType(),
                            previousRouteBest,
                            saved.getMovingDurationSeconds(),
                            saved.getStartedAt())
                    .ifPresent(detected::add);
        }

        List<AchievementResponse> achievements =
                achievementService.award(saved.getId(), saved.getUserId(), detected);

        RouteComparisonResponse comparison = saved.getRouteId() == null
                ? null
                : routeService.compare(saved.getUserId(), saved.getRouteId(), saved.getId()).orElse(null);

        ChallengeResponse.Result challengeResult = challengeId == null
                ? null
                : challengeService.settleFromWorkout(
                                saved.getUserId(), challengeId, saved, !achievements.isEmpty())
                        .orElse(null);

        return new WorkoutResponse(toSummary(saved), gpsPointsOf(saved), achievements, comparison,
                challengeResult);
    }

    /**
     * Resultat d'un enregistrement de seance.
     *
     * @param workout seance telle qu'elle est desormais enregistree
     * @param created {@code false} quand la seance existait deja, c'est-a-dire
     *                quand le client a renvoye un identifiant deja connu ; le
     *                controleur repond alors 200 plutot que 201, ce qui dit au
     *                mobile que son envoi precedent avait bien abouti
     */
    public record Recorded(WorkoutResponse workout, boolean created) {
    }

    @Transactional(readOnly = true)
    public WorkoutResponse getById(UUID userId, UUID workoutId) {
        return sessions.findByIdAndUserId(workoutId, userId)
                .map(this::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Séance introuvable."));
    }

    /**
     * @param sportType filtre optionnel ; {@code null} pour tous les sports
     */
    @Transactional(readOnly = true)
    public Page<WorkoutSummaryResponse> list(UUID userId, SportType sportType, Pageable pageable) {
        Page<WorkoutSession> page = sportType == null
                ? sessions.findByUserId(userId, pageable)
                : sessions.findByUserIdAndSportType(userId, sportType, pageable);
        return page.map(this::toSummary);
    }

    /** Toutes les seances avec leur trace, pour l'export des donnees. */
    @Transactional(readOnly = true)
    public List<WorkoutResponse> exportAll(UUID userId) {
        return sessions.findByUserIdOrderByStartedAtAsc(userId).stream()
                .map(this::toDetail)
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID workoutId) {
        WorkoutSession session = sessions.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Séance introuvable."));
        // Le defi garde son verdict et perd seulement le lien vers la seance :
        // effacer une reussite parce que la trace a disparu serait une punition.
        challengeService.detachFromWorkout(userId, workoutId);
        // Le trace part avec la seance grace au orphanRemoval de l'association,
        // et les trophees avec elle par la cascade declaree en base. Les records
        // COURANTS, eux, se recalculent a la lecture : celui que detenait cette
        // seance revient donc naturellement a la suivante.
        sessions.delete(session);
    }

    private List<GpsPointRequest> sortedTrack(List<GpsPointRequest> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.stream()
                .sorted(Comparator.comparing(GpsPointRequest::recordedAt))
                .toList();
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Vue d'une seance deja enregistree.
     *
     * <p>Les trophees sont relus en base et non recalcules : ils datent du jour
     * ou ils sont tombes, et rejouer la detection donnerait aujourd'hui un
     * resultat different, la seance faisant desormais partie de sa propre
     * reference.
     *
     * <p>La comparaison de parcours et le verdict de defi, eux, ne figurent pas
     * ici : ils appartiennent au moment de l'arrivee. Le classement complet est
     * servi par {@code GET /me/routes/{id}/attempts}, le defi par
     * {@code GET /me/challenges/{id}}.
     */
    private WorkoutResponse toDetail(WorkoutSession session) {
        return new WorkoutResponse(
                toSummary(session),
                gpsPointsOf(session),
                achievementService.forWorkout(session.getId()),
                null,
                null);
    }

    private List<GpsPointResponse> gpsPointsOf(WorkoutSession session) {
        return session.getGpsPoints().stream()
                .map(point -> new GpsPointResponse(
                        point.getPosition(),
                        point.getLatitude(),
                        point.getLongitude(),
                        point.getAltitude(),
                        point.getAccuracy(),
                        point.getSpeed(),
                        point.getRecordedAt()))
                .toList();
    }

    private WorkoutSummaryResponse toSummary(WorkoutSession session) {
        return new WorkoutSummaryResponse(
                session.getId(),
                session.getSportType(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSeconds(),
                session.getMovingDurationSeconds(),
                session.getDistanceMeters(),
                session.getAveragePaceSecondsPerKm(),
                session.getAverageSpeedKmh(),
                session.getMaxSpeedKmh(),
                session.getElevationGainMeters(),
                session.getCaloriesBurned(),
                session.getPerceivedEffort(),
                session.getFeeling(),
                session.getNote());
    }
}
