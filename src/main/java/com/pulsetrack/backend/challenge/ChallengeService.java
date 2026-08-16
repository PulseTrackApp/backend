package com.pulsetrack.backend.challenge;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.achievement.SportPerformanceRow;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeProgressRequest;
import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.challenge.dto.CompleteChallengeRequest;
import com.pulsetrack.backend.challenge.dto.CreateChallengeRequest;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.route.SavedRouteRepository;
import com.pulsetrack.backend.workout.WorkoutSession;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie des defis.
 *
 * <p>Le service tient les transitions d'etat et la coherence ; le jugement, lui,
 * appartient a {@link ChallengeEvaluator}, {@link ChallengePlanner} et
 * {@link DifficultyAssessor}, tous trois sans etat et eprouvables sans base.
 */
@Service
public class ChallengeService {

    private final ChallengeRepository challenges;
    private final SavedRouteRepository routes;
    private final WorkoutSessionRepository sessions;
    private final ChallengePlanner planner;
    private final ChallengeEvaluator evaluator;
    private final DifficultyAssessor difficultyAssessor;
    private final Clock clock;

    /**
     * {@code @Autowired} explicite : la classe expose deux constructeurs, et sans
     * cette marque Spring refuse de choisir et cherche un constructeur sans
     * argument qui n'existe pas.
     */
    @Autowired
    public ChallengeService(ChallengeRepository challenges,
                            SavedRouteRepository routes,
                            WorkoutSessionRepository sessions,
                            ChallengePlanner planner,
                            ChallengeEvaluator evaluator,
                            DifficultyAssessor difficultyAssessor) {
        this(challenges, routes, sessions, planner, evaluator, difficultyAssessor, Clock.systemUTC());
    }

    /** Reservee aux tests, pour piloter le temps sans attendre une echeance. */
    ChallengeService(ChallengeRepository challenges,
                     SavedRouteRepository routes,
                     WorkoutSessionRepository sessions,
                     ChallengePlanner planner,
                     ChallengeEvaluator evaluator,
                     DifficultyAssessor difficultyAssessor,
                     Clock clock) {
        this.challenges = challenges;
        this.routes = routes;
        this.sessions = sessions;
        this.planner = planner;
        this.evaluator = evaluator;
        this.difficultyAssessor = difficultyAssessor;
        this.clock = clock;
    }

    @Transactional
    public ChallengeResponse create(UUID userId, CreateChallengeRequest request) {
        Instant now = clock.instant();

        if (request.routeId() != null && !routes.existsByIdAndUserId(request.routeId(), userId)) {
            throw new ResourceNotFoundException("Parcours introuvable.");
        }
        // Une date limite deja passee produirait un defi expire des sa creation.
        // Le refuser vaut mieux que de rendre un objet mort-ne.
        if (request.expiresOn() != null && request.expiresOn().isBefore(today())) {
            throw new BusinessRuleException("La date limite doit être dans le futur.");
        }

        Challenge challenge = new Challenge(
                UUID.randomUUID(),
                userId,
                titleOf(request),
                request.sportType(),
                request.targetDistanceMeters(),
                request.targetDurationSeconds(),
                request.routeId(),
                request.expiresOn(),
                now);

        return toResponse(challenges.save(challenge));
    }

    @Transactional(readOnly = true)
    public Page<ChallengeResponse> list(UUID userId, Collection<ChallengeStatus> statuses, Pageable pageable) {
        Page<Challenge> page = (statuses == null || statuses.isEmpty())
                ? challenges.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : challenges.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, statuses, pageable);
        // Une seule memoire partagee par toute la page : sans elle, vingt defis
        // du meme sport declencheraient vingt fois la meme lecture d'historique,
        // et autant de lectures de nom de parcours.
        Lookup lookup = new Lookup();
        return page.map(challenge -> toResponse(challenge, lookup));
    }

    @Transactional(readOnly = true)
    public ChallengeResponse getById(UUID userId, UUID challengeId) {
        return toResponse(load(userId, challengeId));
    }

    /**
     * Arme le chronometre et rend le tableau de marche.
     *
     * @throws ConflictException si un autre defi est deja en cours, ou si
     *                           celui-ci est deja joue
     */
    @Transactional
    public ChallengeResponse start(UUID userId, UUID challengeId) {
        Challenge challenge = load(userId, challengeId);

        if (challenge.getStatus() == ChallengeStatus.ACTIVE) {
            throw new ConflictException("Ce défi est déjà en cours.");
        }
        if (challenge.getStatus().isSettled()) {
            throw new ConflictException("Ce défi est terminé, il ne peut plus être lancé.");
        }
        // Deux echeances simultanees ne veulent rien dire, et l'ecran de course
        // n'en affiche qu'une. La base fait respecter la meme regle par un index
        // partiel : cette verification sert a rendre un message clair.
        Optional<Challenge> running = challenges.findByUserIdAndStatus(userId, ChallengeStatus.ACTIVE);
        if (running.isPresent()) {
            throw new ConflictException(
                    "Un défi est déjà en cours : « " + running.get().getTitle() + " ». Termine-le ou abandonne-le.");
        }

        challenge.start(clock.instant());
        return toResponse(challenge);
    }

    @Transactional
    public ChallengeResponse abandon(UUID userId, UUID challengeId) {
        Challenge challenge = load(userId, challengeId);
        if (challenge.getStatus().isSettled()) {
            throw new ConflictException("Ce défi est déjà terminé.");
        }
        challenge.abandon(clock.instant());
        return toResponse(challenge);
    }

    @Transactional
    public void delete(UUID userId, UUID challengeId) {
        challenges.delete(load(userId, challengeId));
    }

    /**
     * Point d'etape en cours d'effort. <strong>N'ecrit rien</strong> : un client
     * qui l'appellerait au mauvais moment ne peut pas faire echouer le defi.
     */
    @Transactional(readOnly = true)
    public ChallengeProgressResponse progress(UUID userId, UUID challengeId, ChallengeProgressRequest request) {
        Challenge challenge = load(userId, challengeId);
        if (challenge.getStatus().isSettled()) {
            throw new ConflictException("Ce défi est terminé.");
        }
        return evaluator.progressOf(challenge, request.elapsedSeconds(), request.distanceMeters());
    }

    /**
     * Regle le defi, a partir d'une seance enregistree ou de chiffres declares.
     *
     * <p>Un defi encore {@code DRAFT} est accepte : quelqu'un qui oublie
     * d'appuyer sur « demarrer » avant de partir ne doit pas perdre sa sortie.
     */
    @Transactional
    public ChallengeResponse complete(UUID userId, UUID challengeId, CompleteChallengeRequest request) {
        Challenge challenge = load(userId, challengeId);
        if (challenge.getStatus().isSettled()) {
            throw new ConflictException("Ce défi est déjà terminé.");
        }

        if (request.referencesWorkout()) {
            WorkoutSession workout = sessions.findByIdAndUserId(request.workoutId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Séance introuvable."));
            settle(challenge, workout.getDistanceMeters(), workout.getMovingDurationSeconds(),
                    workout.getId(), false);
            return toResponse(challenge);
        }

        if (!request.isDeclared()) {
            throw new BusinessRuleException(
                    "Indique la séance qui règle ce défi, ou bien la distance et la durée réalisées.");
        }
        settle(challenge, request.distanceMeters(), request.durationSeconds(), null, false);
        return toResponse(challenge);
    }

    /**
     * Regle le defi que vient de jouer une seance fraichement enregistree.
     *
     * <p>Appelee depuis l'enregistrement de la seance, pour que le mobile n'ait
     * qu'un aller-retour a faire a l'arrivee — ce qui compte quand le reseau
     * revient a peine.
     *
     * @param recordBeaten un record est tombe pendant cette seance ; c'est ce qui
     *                     rend un echec de justesse digne d'etre fete
     * @return vide si le defi n'existe pas, appartient a quelqu'un d'autre ou est
     *         deja joue — un rattachement invalide ne doit pas faire echouer
     *         l'enregistrement d'une seance reellement courue
     */
    @Transactional
    public Optional<ChallengeResponse.Result> settleFromWorkout(UUID userId,
                                                               UUID challengeId,
                                                               WorkoutSession workout,
                                                               boolean recordBeaten) {
        Optional<Challenge> found = challenges.findByIdAndUserId(challengeId, userId);
        if (found.isEmpty() || found.get().getStatus().isSettled()) {
            return Optional.empty();
        }
        Challenge challenge = found.get();
        return Optional.of(settle(challenge, workout.getDistanceMeters(),
                workout.getMovingDurationSeconds(), workout.getId(), recordBeaten));
    }

    /**
     * Ferme les defis jamais tentes dont la date limite est passee.
     *
     * <p>Seuls les {@code DRAFT} sont concernes : un defi arme ne doit pas
     * s'evaporer sous les pieds de quelqu'un qui court a minuit.
     *
     * @return le nombre de defis fermes
     */
    @Transactional
    public int expireOverdue() {
        Instant now = clock.instant();
        List<Challenge> overdue = challenges.findExpiredDrafts(today());
        overdue.forEach(challenge -> challenge.expire(now));
        return overdue.size();
    }

    /** Defis non tentes dont la date limite tombe dans les prochains jours. */
    @Transactional(readOnly = true)
    public List<Challenge> draftsExpiringWithin(int days) {
        LocalDate today = today();
        return challenges.findDraftsExpiringBetween(today, today.plusDays(days));
    }

    /**
     * Detache les defis d'une seance supprimee. Le resultat obtenu reste acquis :
     * effacer une reussite parce que la trace a disparu serait une punition.
     */
    @Transactional
    public void detachFromWorkout(UUID userId, UUID workoutId) {
        challenges.findByUserIdAndWorkoutId(userId, workoutId).forEach(Challenge::detachFromWorkout);
    }

    /** Defis d'un compte, pour l'export des donnees personnelles. */
    @Transactional(readOnly = true)
    public List<ChallengeResponse> exportAll(UUID userId) {
        return challenges.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    private ChallengeResponse.Result settle(Challenge challenge,
                                            double distanceMeters,
                                            long durationSeconds,
                                            UUID workoutId,
                                            boolean recordBeaten) {
        ChallengeResponse.Result result =
                evaluator.evaluate(challenge, distanceMeters, durationSeconds, recordBeaten);
        challenge.settle(result.succeeded(), distanceMeters, durationSeconds, workoutId, clock.instant());
        return result;
    }

    private Challenge load(UUID userId, UUID challengeId) {
        return challenges.findByIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Défi introuvable."));
    }

    /**
     * Le jour courant dans le fuseau du serveur. Les dates limites sont a la
     * journee : la precision d'un fuseau par utilisateur n'apporterait rien et
     * compliquerait le balayage quotidien.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault());
    }

    /** « 10 km en 55 min » : la cible fait un meilleur titre qu'un champ vide. */
    private String titleOf(CreateChallengeRequest request) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        return "%s en %s".formatted(
                Wording.distance(request.targetDistanceMeters()),
                Wording.duration(request.targetDurationSeconds()));
    }

    private ChallengeResponse toResponse(Challenge challenge) {
        return toResponse(challenge, new Lookup());
    }

    /**
     * Memoire de lectures partagee le temps d'une reponse.
     *
     * <p>L'appreciation d'avance a besoin de l'historique du sport, et le titre a
     * besoin du nom du parcours. Sans cette memoire, une page de vingt defis
     * ferait quarante requetes pour repeter les memes quatre reponses.
     */
    private final class Lookup {

        private final Map<SportType, List<SportPerformanceRow>> histories = new EnumMap<>(SportType.class);

        private final Map<UUID, Optional<String>> routeNames = new HashMap<>();

        List<SportPerformanceRow> historyOf(UUID userId, SportType sport) {
            return histories.computeIfAbsent(sport, key -> sessions.performancesOf(userId, key));
        }

        String routeNameOf(UUID userId, UUID routeId) {
            return routeNames
                    .computeIfAbsent(routeId, key -> routes.findNameByIdAndUserId(key, userId))
                    .orElse(null);
        }
    }

    private ChallengeResponse toResponse(Challenge challenge, Lookup lookup) {
        // Le plan n'a de sens qu'une fois le defi arme : le remettre sur un
        // brouillon ferait jouer des alertes avant meme le depart.
        ChallengePlanResponse plan = challenge.getStatus() == ChallengeStatus.ACTIVE
                ? planner.planFor(challenge)
                : null;

        ChallengeResponse.Result result = challenge.getSucceeded() == null
                ? null
                : evaluator.evaluate(challenge,
                        challenge.getAchievedDistanceMeters(),
                        challenge.getAchievedDurationSeconds(),
                        false);

        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getSportType(),
                challenge.getTargetDistanceMeters(),
                challenge.getTargetDurationSeconds(),
                challenge.requiredPaceSecondsPerKm(),
                round(challenge.requiredSpeedKmh()),
                challenge.getRouteId(),
                routeNameOf(challenge, lookup),
                challenge.getStatus(),
                challenge.getExpiresOn(),
                challenge.getCreatedAt(),
                challenge.getStartedAt(),
                challenge.getDeadlineAt(),
                challenge.getCompletedAt(),
                challenge.getWorkoutId(),
                difficultyOf(challenge, lookup),
                plan,
                result);
    }

    /**
     * L'appreciation d'avance ne se calcule que tant que le defi peut encore etre
     * tente. Une fois joue, c'est le resultat qui compte, et interroger
     * l'historique pour rien couterait une requete par ligne de liste.
     */
    private ChallengeResponse.Difficulty difficultyOf(Challenge challenge, Lookup lookup) {
        if (challenge.getStatus().isSettled()) {
            return null;
        }
        return difficultyAssessor.assess(
                challenge.getSportType(),
                challenge.requiredPaceSecondsPerKm(),
                lookup.historyOf(challenge.getUserId(), challenge.getSportType()));
    }

    private String routeNameOf(Challenge challenge, Lookup lookup) {
        if (challenge.getRouteId() == null) {
            return null;
        }
        return lookup.routeNameOf(challenge.getUserId(), challenge.getRouteId());
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
