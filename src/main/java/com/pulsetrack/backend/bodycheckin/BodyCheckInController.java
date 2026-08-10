package com.pulsetrack.backend.bodycheckin;

import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInRequest;
import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInResponse;
import com.pulsetrack.backend.bodycheckin.dto.BodyProgressResponse;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Releves physiques de l'utilisateur courant.
 */
@RestController
@RequestMapping("/api/v1/me/body-checkins")
public class BodyCheckInController {

    private final BodyCheckInService bodyCheckInService;

    public BodyCheckInController(BodyCheckInService bodyCheckInService) {
        this.bodyCheckInService = bodyCheckInService;
    }

    /**
     * {@code PUT} et non {@code POST} : l'operation est identifiee par la date du
     * releve et rejouable sans creer de doublon. Un mobile qui reessaie apres une
     * coupure reseau ne risque pas de dedoubler la courbe.
     */
    @PutMapping
    public BodyCheckInResponse save(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody BodyCheckInRequest request) {
        return bodyCheckInService.save(AuthenticatedUser.idOf(jwt), request);
    }

    @GetMapping
    public Page<BodyCheckInResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "checkinDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return bodyCheckInService.list(AuthenticatedUser.idOf(jwt), pageable);
    }

    /** Serie complete et indicateurs, pour tracer les courbes de progression. */
    @GetMapping("/progress")
    public BodyProgressResponse progress(@AuthenticationPrincipal Jwt jwt) {
        return bodyCheckInService.progress(AuthenticatedUser.idOf(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        bodyCheckInService.delete(AuthenticatedUser.idOf(jwt), id);
    }
}
