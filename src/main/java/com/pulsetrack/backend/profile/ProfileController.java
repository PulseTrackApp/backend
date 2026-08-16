package com.pulsetrack.backend.profile;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.profile.dto.ProfilePatchRequest;
import com.pulsetrack.backend.profile.dto.ProfileRequest;
import com.pulsetrack.backend.profile.dto.ProfileResponse;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * Enregistre le profil <strong>en entier</strong> : ce qui n'est pas envoye
     * est efface. C'est l'operation de l'ecran d'accueil, qui saisit tout d'un
     * coup, et le seul moyen de vider un champ facultatif.
     *
     * <p>Un ecran qui ne corrige qu'un champ doit utiliser {@code PATCH} : voir
     * la mise en garde ci-dessous.
     */
    @PutMapping
    public ProfileResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return profileService.save(AuthenticatedUser.idOf(jwt), request);
    }

    /**
     * Modifie les seuls champs fournis.
     *
     * <p><strong>A preferer partout sauf a l'inscription.</strong> Un
     * {@code PUT} incomplet passe la validation tant que les champs obligatoires
     * sont la, et efface au passage la date de naissance et le sexe. C'est
     * exactement ce que l'utilisateur avait pris la peine de renseigner en plus
     * qui disparaissait, sans aucun signal.
     */
    @PatchMapping
    public ProfileResponse patch(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ProfilePatchRequest request) {
        return profileService.patch(AuthenticatedUser.idOf(jwt), request);
    }
}
