package com.pulsetrack.backend.workout;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.achievement.AchievementService;
import com.pulsetrack.backend.achievement.dto.SportRecordsResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.workout.dto.CreateWorkoutRequest;
import com.pulsetrack.backend.workout.dto.WorkoutResponse;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seances de l'utilisateur courant.
 */
@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final AchievementService achievementService;

    public WorkoutController(WorkoutService workoutService, AchievementService achievementService) {
        this.workoutService = workoutService;
        this.achievementService = achievementService;
    }

    /**
     * Enregistre une seance terminee.
     *
     * <p>Rejouable quand le client fournit l'identifiant : un renvoi de la meme
     * seance repond 200 avec l'enregistrement existant, au lieu de 201 et d'un
     * doublon. C'est indispensable des lors que le mobile suit une course en
     * arriere-plan et televerse a la fin, quand le reseau revient.
     */
    @PostMapping
    public ResponseEntity<WorkoutResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateWorkoutRequest request) {
        WorkoutService.Recorded recorded = workoutService.create(AuthenticatedUser.idOf(jwt), request);
        URI location = URI.create("/api/v1/workouts/" + recorded.workout().summary().id());
        return recorded.created()
                ? ResponseEntity.created(location).body(recorded.workout())
                : ResponseEntity.ok().location(location).body(recorded.workout());
    }

    /**
     * Historique pagine, du plus recent au plus ancien.
     *
     * @param sport filtre optionnel sur le type de sport
     */
    @GetMapping
    public Page<WorkoutSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) SportType sport,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return workoutService.list(AuthenticatedUser.idOf(jwt), sport, pageable);
    }

    /**
     * Records courants, sport par sport.
     *
     * <p>Declaree avant {@code /{id}} pour la lisibilite, mais l'ordre du fichier
     * n'y change rien : Spring fait primer un segment litteral sur une variable
     * de chemin, {@code /records} ne sera donc jamais pris pour un identifiant.
     *
     * <p>Route placee sous {@code /workouts} et non sous les statistiques a
     * dessein : le module {@code STATS} est ferme par defaut, et les records
     * doivent rester lisibles sur un compte neuf.
     *
     * @param sport un seul sport, ou tous ceux pratiques si le parametre est omis
     */
    @GetMapping("/records")
    public List<SportRecordsResponse> records(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) SportType sport) {
        return achievementService.recordsOf(AuthenticatedUser.idOf(jwt), sport);
    }

    @GetMapping("/{id}")
    public WorkoutResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return workoutService.getById(AuthenticatedUser.idOf(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        workoutService.delete(AuthenticatedUser.idOf(jwt), id);
    }
}
