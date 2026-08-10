package com.pulsetrack.backend.workout;

import java.net.URI;
import java.util.UUID;

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

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping
    public ResponseEntity<WorkoutResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateWorkoutRequest request) {
        WorkoutResponse created = workoutService.create(AuthenticatedUser.idOf(jwt), request);
        URI location = URI.create("/api/v1/workouts/" + created.summary().id());
        return ResponseEntity.created(location).body(created);
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
