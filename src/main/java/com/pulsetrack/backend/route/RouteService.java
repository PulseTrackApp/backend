package com.pulsetrack.backend.route;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.route.dto.CreateRouteRequest;
import com.pulsetrack.backend.route.dto.RouteAttemptResponse;
import com.pulsetrack.backend.route.dto.RouteComparisonResponse;
import com.pulsetrack.backend.route.dto.RouteResponse;
import com.pulsetrack.backend.workout.WorkoutSession;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parcours enregistres et classement des passages.
 *
 * <p>Deux idees portent tout ce service :
 * <ul>
 *   <li>un parcours est le <strong>trace</strong> d'une seance, decime pour etre
 *       dessine, jamais pour etre mesure. Sa distance reste celle de la seance
 *       d'origine, estimee par le filtre de Kalman ;</li>
 *   <li>le rattachement d'une seance a un parcours est <strong>declaratif</strong>.
 *       Le serveur ne verifie pas que la trace suit le circuit : comparer deux
 *       traces bruitees demande un appariement point a point qui coute cher et se
 *       trompe. C'est l'utilisateur qui dit ce qu'il rejoue.</li>
 * </ul>
 */
@Service
public class RouteService {

    /**
     * Sous dix points, il n'y a pas de forme a dessiner : une seance en salle ou
     * un trace perdu ne font pas un circuit.
     */
    static final int MIN_POINTS = 10;

    /** Sous deux cents metres, rien a rejouer. */
    static final double MIN_DISTANCE_METERS = 200d;

    /**
     * Ecart maximal entre depart et arrivee pour parler de boucle. Cent metres
     * couvrent le pate de maisons ou la largeur d'un stade, et restent bien
     * au-dessus du bruit du GPS.
     */
    static final double LOOP_TOLERANCE_METERS = 100d;

    private final SavedRouteRepository routes;
    private final WorkoutSessionRepository sessions;
    private final TrackSimplifier simplifier;

    public RouteService(SavedRouteRepository routes,
                        WorkoutSessionRepository sessions,
                        TrackSimplifier simplifier) {
        this.routes = routes;
        this.sessions = sessions;
        this.simplifier = simplifier;
    }

    /**
     * Enregistre le trace d'une seance sous un nom, pour pouvoir le reprendre.
     *
     * @throws ResourceNotFoundException si la seance n'existe pas, ou appartient
     *                                   a quelqu'un d'autre
     * @throws BusinessRuleException     si la seance n'a pas de trace exploitable
     * @throws ConflictException         si un parcours porte deja ce nom
     */
    @Transactional
    public RouteResponse create(UUID userId, CreateRouteRequest request) {
        WorkoutSession source = sessions.findByIdAndUserId(request.workoutId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Séance introuvable."));

        String name = request.name().trim();
        if (routes.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Un parcours porte déjà ce nom.");
        }

        List<TrackSimplifier.Point> track = source.getGpsPoints().stream()
                .map(point -> new TrackSimplifier.Point(
                        point.getLatitude(), point.getLongitude(), point.getAltitude()))
                .toList();

        if (track.size() < MIN_POINTS) {
            throw new BusinessRuleException(
                    "Cette séance n'a pas de trace exploitable : un parcours demande au moins "
                            + MIN_POINTS + " points GPS.");
        }
        if (source.getDistanceMeters() < MIN_DISTANCE_METERS) {
            throw new BusinessRuleException(
                    "Cette séance est trop courte pour faire un parcours : "
                            + Math.round(MIN_DISTANCE_METERS) + " mètres au minimum.");
        }

        List<TrackSimplifier.Point> simplified = simplifier.simplify(track);
        SavedRoute route = new SavedRoute(
                UUID.randomUUID(),
                userId,
                name,
                source.getSportType(),
                source.getDistanceMeters(),
                source.getElevationGainMeters(),
                isLoop(track),
                source.getId(),
                Instant.now());

        fillPoints(route, simplified, source.getDistanceMeters());

        SavedRoute saved = routes.save(route);
        // Le parcours vient d'etre cree : aucune tentative, et la seance
        // d'origine n'en est pas une tant que l'utilisateur ne l'a pas rejouee.
        return toResponse(saved, new RouteAttemptStats(saved.getId(), 0, null, null), true);
    }

    @Transactional(readOnly = true)
    public Page<RouteResponse> list(UUID userId, SportType sport, Pageable pageable) {
        Page<SavedRoute> page = sport == null
                ? routes.findByUserId(userId, pageable)
                : routes.findByUserIdAndSportType(userId, sport, pageable);

        Map<UUID, RouteAttemptStats> stats = attemptStatsOf(userId, page.getContent());
        return page.map(route -> toResponse(route, statsFor(stats, route), false));
    }

    @Transactional(readOnly = true)
    public RouteResponse getById(UUID userId, UUID routeId) {
        SavedRoute route = load(userId, routeId);
        Map<UUID, RouteAttemptStats> stats = attemptStatsOf(userId, List.of(route));
        return toResponse(route, statsFor(stats, route), true);
    }

    @Transactional
    public RouteResponse rename(UUID userId, UUID routeId, String newName) {
        SavedRoute route = load(userId, routeId);
        String name = newName.trim();
        if (!route.getName().equalsIgnoreCase(name) && routes.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Un parcours porte déjà ce nom.");
        }
        route.rename(name, Instant.now());
        Map<UUID, RouteAttemptStats> stats = attemptStatsOf(userId, List.of(route));
        return toResponse(route, statsFor(stats, route), true);
    }

    /**
     * Supprime un parcours sans toucher aux seances qui le rejouaient : celles-ci
     * ont bien eu lieu, elles perdent seulement leur rattachement.
     */
    @Transactional
    public void delete(UUID userId, UUID routeId) {
        SavedRoute route = load(userId, routeId);
        sessions.detachFromRoute(userId, routeId);
        routes.delete(route);
    }

    /** Passages sur un parcours, du plus rapide au plus lent. */
    @Transactional(readOnly = true)
    public List<RouteAttemptResponse> attemptsOf(UUID userId, UUID routeId) {
        // Verifie l'appartenance avant de lire les tentatives : sans cela, un
        // identifiant devine rendrait le classement de quelqu'un d'autre.
        load(userId, routeId);

        List<WorkoutSession> ranked = sessions.findByUserIdAndRouteIdOrderByMovingDurationSecondsAsc(
                userId, routeId);
        if (ranked.isEmpty()) {
            return List.of();
        }

        long best = ranked.get(0).getMovingDurationSeconds();
        List<RouteAttemptResponse> attempts = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            WorkoutSession attempt = ranked.get(index);
            attempts.add(new RouteAttemptResponse(
                    index + 1,
                    attempt.getId(),
                    attempt.getStartedAt(),
                    attempt.getDurationSeconds(),
                    attempt.getMovingDurationSeconds(),
                    attempt.getDistanceMeters(),
                    attempt.getAveragePaceSecondsPerKm(),
                    index == 0,
                    attempt.getMovingDurationSeconds() - best));
        }
        return List.copyOf(attempts);
    }

    /**
     * Verifie qu'un parcours appartient bien au compte, avant de rattacher une
     * seance. Rend le nom, dont la comparaison a besoin pour son message.
     *
     * @throws ResourceNotFoundException si le parcours n'existe pas pour ce compte
     */
    @Transactional(readOnly = true)
    public SportType requireOwned(UUID userId, UUID routeId) {
        return routes.findByIdAndUserId(routeId, userId)
                .map(SavedRoute::getSportType)
                .orElseThrow(() -> new ResourceNotFoundException("Parcours introuvable."));
    }

    /**
     * Compare une seance fraichement enregistree aux passages precedents.
     *
     * <p>Appelee apres l'enregistrement, une fois la seance rattachee : elle
     * figure donc dans le classement, a sa place.
     *
     * @return vide si le parcours a disparu entre-temps
     */
    @Transactional(readOnly = true)
    public Optional<RouteComparisonResponse> compare(UUID userId, UUID routeId, UUID workoutId) {
        Optional<SavedRoute> route = routes.findByIdAndUserId(routeId, userId);
        if (route.isEmpty()) {
            return Optional.empty();
        }

        List<WorkoutSession> byTime = sessions.findByUserIdAndRouteIdOrderByMovingDurationSecondsAsc(
                userId, routeId);
        int position = -1;
        for (int index = 0; index < byTime.size(); index++) {
            if (byTime.get(index).getId().equals(workoutId)) {
                position = index;
                break;
            }
        }
        if (position < 0) {
            return Optional.empty();
        }

        long duration = byTime.get(position).getMovingDurationSeconds();
        int rank = position + 1;

        List<WorkoutSession> earlier = byTime.stream()
                .filter(session -> !session.getId().equals(workoutId))
                .toList();
        Long bestPrevious = earlier.stream()
                .map(WorkoutSession::getMovingDurationSeconds)
                .min(Long::compare)
                .orElse(null);
        Long previousAttempt = earlier.stream()
                .max(java.util.Comparator.comparing(WorkoutSession::getStartedAt))
                .map(WorkoutSession::getMovingDurationSeconds)
                .orElse(null);

        boolean newBest = bestPrevious != null && duration < bestPrevious;

        return Optional.of(new RouteComparisonResponse(
                routeId,
                route.get().getName(),
                earlier.size() + 1,
                byTime.size(),
                duration,
                bestPrevious,
                previousAttempt,
                bestPrevious == null ? null : duration - bestPrevious,
                previousAttempt == null ? null : duration - previousAttempt,
                newBest,
                rank,
                headlineOf(bestPrevious, duration, newBest),
                messageOf(route.get().getName(), bestPrevious, duration, newBest)));
    }

    /** Meilleur temps connu sur un parcours, hors une seance donnee. */
    @Transactional(readOnly = true)
    public Long bestDurationExcluding(UUID userId, UUID routeId, UUID excludedWorkoutId) {
        return sessions.findByUserIdAndRouteIdOrderByMovingDurationSecondsAsc(userId, routeId).stream()
                .filter(session -> !session.getId().equals(excludedWorkoutId))
                .map(WorkoutSession::getMovingDurationSeconds)
                .min(Long::compare)
                .orElse(null);
    }

    /** Parcours d'un compte, pour l'export des donnees personnelles. */
    @Transactional(readOnly = true)
    public List<RouteResponse> exportAll(UUID userId) {
        List<SavedRoute> all = routes.findByUserIdOrderByCreatedAtAsc(userId);
        Map<UUID, RouteAttemptStats> stats = attemptStatsOf(userId, all);
        return all.stream().map(route -> toResponse(route, statsFor(stats, route), true)).toList();
    }

    private SavedRoute load(UUID userId, UUID routeId) {
        return routes.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcours introuvable."));
    }

    private Map<UUID, RouteAttemptStats> attemptStatsOf(UUID userId, List<SavedRoute> forRoutes) {
        if (forRoutes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = forRoutes.stream().map(SavedRoute::getId).toList();
        Map<UUID, RouteAttemptStats> byRoute = new HashMap<>();
        for (RouteAttemptStats stats : sessions.attemptStatsOf(userId, ids)) {
            byRoute.put(stats.routeId(), stats);
        }
        return byRoute;
    }

    private RouteAttemptStats statsFor(Map<UUID, RouteAttemptStats> stats, SavedRoute route) {
        return stats.getOrDefault(route.getId(), new RouteAttemptStats(route.getId(), 0, null, null));
    }

    /**
     * Repartit la distance officielle le long du trace.
     *
     * <p>Les proportions entre segments viennent des positions brutes, mais leur
     * <em>somme</em> est ramenee a la distance estimee par le filtre de Kalman.
     * Sans ce redressement, le dernier point afficherait « km 3,1 » sur un
     * parcours annonce a 2,4 km, et l'utilisateur croirait a un bug.
     */
    private void fillPoints(SavedRoute route, List<TrackSimplifier.Point> points, double officialDistance) {
        double[] raw = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            raw[index] = raw[index - 1]
                    + TrackSimplifier.haversineMeters(points.get(index - 1), points.get(index));
        }

        double rawTotal = raw[raw.length - 1];
        double scale = rawTotal > 0 ? officialDistance / rawTotal : 0;

        for (int index = 0; index < points.size(); index++) {
            TrackSimplifier.Point point = points.get(index);
            route.addPoint(point.latitude(), point.longitude(), point.altitude(), raw[index] * scale);
        }
    }

    private boolean isLoop(List<TrackSimplifier.Point> track) {
        return TrackSimplifier.haversineMeters(track.get(0), track.get(track.size() - 1))
                <= LOOP_TOLERANCE_METERS;
    }

    private String headlineOf(Long bestPrevious, long duration, boolean newBest) {
        if (bestPrevious == null) {
            return "Premier passage";
        }
        if (newBest) {
            return "Nouveau meilleur temps";
        }
        return duration == bestPrevious ? "À la seconde près" : "Passage enregistré";
    }

    private String messageOf(String routeName, Long bestPrevious, long duration, boolean newBest) {
        if (bestPrevious == null) {
            return "Premier passage sur %s en %s. C'est le temps à battre."
                    .formatted(routeName, Wording.duration(duration));
        }
        if (newBest) {
            return "%s de mieux que ton record sur %s."
                    .formatted(Wording.duration(bestPrevious - duration), routeName);
        }
        if (duration == bestPrevious) {
            return "Exactement ton meilleur temps sur %s. La prochaine sera la bonne.".formatted(routeName);
        }
        return "%s de plus que ton record sur %s, qui reste à %s."
                .formatted(Wording.duration(duration - bestPrevious), routeName,
                        Wording.duration(bestPrevious));
    }

    private RouteResponse toResponse(SavedRoute route, RouteAttemptStats stats, boolean withPoints) {
        List<RouteResponse.RoutePointResponse> points = withPoints
                ? route.getPoints().stream()
                        .map(point -> new RouteResponse.RoutePointResponse(
                                point.getPosition(),
                                point.getLatitude(),
                                point.getLongitude(),
                                point.getAltitude(),
                                round(point.getCumulativeDistanceMeters())))
                        .toList()
                : null;

        return new RouteResponse(
                route.getId(),
                route.getName(),
                route.getSportType(),
                round(route.getDistanceMeters()),
                round(route.getElevationGainMeters()),
                route.isLoop(),
                route.getPointCount(),
                route.getSourceWorkoutId(),
                route.getCreatedAt(),
                (int) stats.attemptCount(),
                stats.bestMovingDurationSeconds(),
                stats.lastAttemptAt(),
                points);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
