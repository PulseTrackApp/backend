package com.pulsetrack.backend.route;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.route.dto.CreateRouteRequest;
import com.pulsetrack.backend.route.dto.RenameRouteRequest;
import com.pulsetrack.backend.route.dto.RouteAttemptResponse;
import com.pulsetrack.backend.route.dto.RouteResponse;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parcours enregistres de l'utilisateur courant : les circuits qu'il veut
 * reprendre.
 *
 * <p>Rejouer un parcours ne passe pas par ces routes mais par l'enregistrement
 * de la seance : {@code POST /api/v1/workouts} avec {@code routeId}. La
 * comparaison avec les passages precedents arrive alors dans la reponse, au
 * moment ou elle interesse l'utilisateur.
 */
@RestController
@RequestMapping("/api/v1/me/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * Enregistre le trace d'une seance passee sous un nom.
     *
     * <p>On ne cree pas un parcours de toutes pieces : un circuit qu'on veut
     * reprendre est forcement un circuit qu'on a fait.
     */
    @PostMapping
    public ResponseEntity<RouteResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateRouteRequest request) {
        RouteResponse created = routeService.create(AuthenticatedUser.idOf(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/me/routes/" + created.id())).body(created);
    }

    /**
     * Liste paginee, <strong>sans le trace</strong> : {@code points} y est nul.
     * Renvoyer trois cents points par ligne couterait un demi-megaoctet pour
     * dessiner vingt vignettes.
     */
    @GetMapping
    public Page<RouteResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) SportType sport,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return routeService.list(AuthenticatedUser.idOf(jwt), sport, pageable);
    }

    /** Detail d'un parcours, trace compris. */
    @GetMapping("/{id}")
    public RouteResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return routeService.getById(AuthenticatedUser.idOf(jwt), id);
    }

    /** Seul le nom se modifie : la geometrie est le releve d'une sortie reelle. */
    @PutMapping("/{id}")
    public RouteResponse rename(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID id,
                                @Valid @RequestBody RenameRouteRequest request) {
        return routeService.rename(AuthenticatedUser.idOf(jwt), id, request.name());
    }

    /**
     * Supprime le parcours. <strong>Aucune seance n'est supprimee</strong> :
     * celles qui le rejouaient perdent seulement leur rattachement.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        routeService.delete(AuthenticatedUser.idOf(jwt), id);
    }

    /**
     * Classement des passages, du plus rapide au plus lent.
     *
     * <p>Non pagine : le nombre de fois qu'on refait le meme circuit reste a
     * l'echelle d'une liste qu'on lit d'un coup d'oeil.
     */
    @GetMapping("/{id}/attempts")
    public List<RouteAttemptResponse> attempts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return routeService.attemptsOf(AuthenticatedUser.idOf(jwt), id);
    }
}
