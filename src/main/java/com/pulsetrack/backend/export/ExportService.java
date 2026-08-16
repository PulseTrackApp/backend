package com.pulsetrack.backend.export;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.challenge.ChallengeService;
import com.pulsetrack.backend.coach.CoachService;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.export.dto.UserDataExport;
import com.pulsetrack.backend.goal.GoalService;
import com.pulsetrack.backend.profile.ProfileService;
import com.pulsetrack.backend.route.RouteService;
import com.pulsetrack.backend.user.UserRepository;
import com.pulsetrack.backend.workout.WorkoutService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produit une archive complete des donnees d'un compte.
 *
 * <p>Ce service ne detient rien : il assemble ce que chaque domaine sait
 * exporter. Ajouter un domaine demain, c'est ajouter une ligne ici — et surtout
 * ne pas l'oublier, sinon l'archive devient silencieusement incomplete.
 */
@Service
public class ExportService {

    private final UserRepository users;
    private final ProfileService profileService;
    private final WorkoutService workoutService;
    private final BodyCheckInService bodyCheckInService;
    private final GoalService goalService;
    private final CoachService coachService;
    private final RouteService routeService;
    private final ChallengeService challengeService;

    public ExportService(UserRepository users,
                         ProfileService profileService,
                         WorkoutService workoutService,
                         BodyCheckInService bodyCheckInService,
                         GoalService goalService,
                         CoachService coachService,
                         RouteService routeService,
                         ChallengeService challengeService) {
        this.users = users;
        this.profileService = profileService;
        this.workoutService = workoutService;
        this.bodyCheckInService = bodyCheckInService;
        this.goalService = goalService;
        this.coachService = coachService;
        this.routeService = routeService;
        this.challengeService = challengeService;
    }

    /**
     * Assemble l'archive en une seule transaction en lecture : toutes les parties
     * decrivent ainsi le meme instant, sans risque qu'une seance enregistree
     * pendant l'export apparaisse dans une liste et pas dans une autre.
     */
    @Transactional(readOnly = true)
    public UserDataExport export(UUID userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));

        // `progress` renvoie deja la serie complete, sans borne de date : passer
        // par LocalDate.MIN/MAX depasserait la plage acceptee par PostgreSQL.
        var allCheckIns = bodyCheckInService.progress(userId).series();

        return new UserDataExport(
                Instant.now(),
                UserDataExport.CURRENT_FORMAT_VERSION,
                user.getEmail(),
                user.getCreatedAt(),
                profileService.findByUserId(userId).orElse(null),
                workoutService.exportAll(userId),
                allCheckIns,
                goalService.exportAll(userId),
                coachService.exportAll(userId),
                routeService.exportAll(userId),
                challengeService.exportAll(userId));
    }
}
