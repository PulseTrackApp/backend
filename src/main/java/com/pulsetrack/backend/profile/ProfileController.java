package com.pulsetrack.backend.profile;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.profile.dto.ProfileRequest;
import com.pulsetrack.backend.profile.dto.ProfileResponse;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil de l'utilisateur courant.
 *
 * <p>L'URL parle de {@code /me} et non de {@code /users/{id}} : l'identite vient
 * du jeton, jamais du chemin, ce qui supprime par construction le risque de lire
 * le profil d'un autre.
 */
@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getByUserId(AuthenticatedUser.idOf(jwt));
    }

    @PutMapping
    public ProfileResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return profileService.save(AuthenticatedUser.idOf(jwt), request);
    }
}
