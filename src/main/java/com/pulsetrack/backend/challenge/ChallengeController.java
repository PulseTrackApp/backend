package com.pulsetrack.backend.challenge;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.challenge.dto.ChallengeProgressRequest;
import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.challenge.dto.CompleteChallengeRequest;
import com.pulsetrack.backend.challenge.dto.CreateChallengeRequest;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Defis chronometres de l'utilisateur courant : parcourir telle distance en tel
 * temps.
 *
 * <p><strong>Ce controleur n'est pas appele pendant l'effort.</strong> Les
 * alertes a l'approche de l'echeance sont jouees par le telephone a partir du
 * plan remis au depart : le reseau est mauvais quand on bouge, et une alerte qui
 * attend une reponse HTTP arrive apres l'echeance qu'elle annonce.
 */
@RestController
@RequestMapping("/api/v1/me/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping
    public ResponseEntity<ChallengeResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CreateChallengeRequest request) {
        ChallengeResponse created = challengeService.create(AuthenticatedUser.idOf(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/me/challenges/" + created.id())).body(created);
    }

    /**
     * @param status filtre facultatif ; plusieurs valeurs separees par une
     *               virgule, par exemple {@code ?status=DRAFT,ACTIVE}
     */
    @GetMapping
    public Page<ChallengeResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<ChallengeStatus> status,
            @PageableDefault(size = 20) Pageable pageable) {
        return challengeService.list(AuthenticatedUser.idOf(jwt), status, pageable);
    }

    @GetMapping("/{id}")
    public ChallengeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return challengeService.getById(AuthenticatedUser.idOf(jwt), id);
    }

    /**
     * Arme le chronometre. La reponse porte le {@code plan} : seuils et messages
     * a jouer localement pendant la course.
     *
     * <p>Un seul defi arme a la fois — un second repond {@code 409}.
     */
    @PostMapping("/{id}/start")
    public ChallengeResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return challengeService.start(AuthenticatedUser.idOf(jwt), id);
    }

    /**
     * Point d'etape a la demande, pour un ecran de suivi.
     *
     * <p>Facultatif, et sans effet de bord : il n'ecrit rien, ne consomme rien et
     * ne peut pas faire echouer le defi. Le plan suffit aux alertes.
     */
    @PostMapping("/{id}/progress")
    public ChallengeProgressResponse progress(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody ChallengeProgressRequest request) {
        return challengeService.progress(AuthenticatedUser.idOf(jwt), id, request);
    }

    /**
     * Regle le defi.
     *
     * <p>L'application mobile n'a normalement pas besoin de cette route : passer
     * {@code challengeId} a {@code POST /api/v1/workouts} enregistre la seance et
     * regle le defi dans le meme appel.
     */
    @PostMapping("/{id}/complete")
    public ChallengeResponse complete(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody CompleteChallengeRequest request) {
        return challengeService.complete(AuthenticatedUser.idOf(jwt), id, request);
    }

    /** Ni reussite ni echec : un renoncement, qui libere la place pour un autre. */
    @PostMapping("/{id}/abandon")
    public ChallengeResponse abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return challengeService.abandon(AuthenticatedUser.idOf(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        challengeService.delete(AuthenticatedUser.idOf(jwt), id);
    }
}
