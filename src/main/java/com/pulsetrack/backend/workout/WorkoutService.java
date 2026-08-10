package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.profile.ProfileService;
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

    public WorkoutService(WorkoutSessionRepository sessions,
                          WorkoutMetricsCalculator calculator,
                          ProfileService profileService) {
        this.sessions = sessions;
        this.calculator = calculator;
        this.profileService = profileService;
    }

    /**
     * Calcule les metriques puis persiste la seance et son trace.
     *
     * @throws BusinessRuleException     si la fenetre de temps est incoherente
     * @throws ResourceNotFoundException si le profil (donc le poids) manque
     */
    @Transactional
    public WorkoutResponse create(UUID userId, CreateWorkoutRequest request) {
        if (!request.endedAt().isAfter(request.startedAt())) {
            throw new BusinessRuleException("La fin de seance doit etre posterieure au debut.");
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

        WorkoutSession session = new WorkoutSession(
                userId,
                request.sportType(),
                request.startedAt(),
                request.endedAt(),
                metrics,
                request.perceivedEffort(),
                request.feeling(),
                normalizeNote(request.note()),
                Instant.now());

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

        return toDetail(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public WorkoutResponse getById(UUID userId, UUID workoutId) {
        return sessions.findByIdAndUserId(workoutId, userId)
                .map(this::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Seance introuvable."));
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
                .orElseThrow(() -> new ResourceNotFoundException("Seance introuvable."));
        // Le trace part avec la seance grace au orphanRemoval de l'association.
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

    private WorkoutResponse toDetail(WorkoutSession session) {
        List<GpsPointResponse> points = session.getGpsPoints().stream()
                .map(point -> new GpsPointResponse(
                        point.getPosition(),
                        point.getLatitude(),
                        point.getLongitude(),
                        point.getAltitude(),
                        point.getAccuracy(),
                        point.getSpeed(),
                        point.getRecordedAt()))
                .toList();
        return new WorkoutResponse(toSummary(session), points);
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
