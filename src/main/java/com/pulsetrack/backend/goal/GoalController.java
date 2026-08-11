package com.pulsetrack.backend.goal;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.goal.dto.GoalRequest;
import com.pulsetrack.backend.goal.dto.GoalResponse;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Objectifs de l'utilisateur courant.
 *
 * <p>La progression n'est pas exposee ici mais dans le bilan hebdomadaire
 * ({@code /api/v1/me/weekly-summary}) : elle se calcule a partir des seances, et
 * le dashboard a besoin des deux dans la meme reponse.
 */
@RestController
@RequestMapping("/api/v1/me/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * Objectifs de l'utilisateur, page par page.
     *
     * <p>Pagine comme les seances et les pesees : avec {@code activeOnly=false}
     * la liste contient les archives, et grossit d'un objectif par semaine et
     * par type. Le defaut de vingt suffit largement a l'ecran des objectifs en
     * cours, dont le nombre est borne par celui des types.
     *
     * @param activeOnly {@code true} par defaut ; passer {@code false} pour voir
     *                   aussi les objectifs archives
     */
    @GetMapping
    public Page<GoalResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return goalService.list(AuthenticatedUser.idOf(jwt), activeOnly, pageable);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody GoalRequest request) {
        GoalResponse created = goalService.create(AuthenticatedUser.idOf(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/me/goals/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id,
                               @Valid @RequestBody GoalRequest request) {
        return goalService.update(AuthenticatedUser.idOf(jwt), id, request);
    }

    /** Sort l'objectif de la course tout en conservant sa trace. */
    @PostMapping("/{id}/archive")
    public GoalResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return goalService.archive(AuthenticatedUser.idOf(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        goalService.delete(AuthenticatedUser.idOf(jwt), id);
    }
}
