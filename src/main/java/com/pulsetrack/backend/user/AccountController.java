package com.pulsetrack.backend.user;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.ChangePasswordRequest;
import com.pulsetrack.backend.user.dto.DeleteAccountRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le compte lui-meme : mot de passe et suppression.
 *
 * <p>Sous {@code /me} comme le profil, et pour la meme raison : l'identite vient
 * du jeton, jamais du chemin. Personne ne peut donc supprimer le compte d'un
 * autre en changeant un identifiant dans l'URL.
 *
 * <p>Ces routes ne relevent d'aucun module : un administrateur peut fermer
 * l'assistant ou les statistiques, jamais la capacite de changer son mot de
 * passe ou de partir.
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountService accountService;
    private final AuthRateLimiter rateLimiter;

    public AccountController(AccountService accountService, AuthRateLimiter rateLimiter) {
        this.accountService = accountService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Change le mot de passe et renvoie une session neuve.
     *
     * <p>Le quota est consomme avant l'appel au service : c'est la verification
     * bcrypt du mot de passe actuel qui coute cher, et un jeton vole servirait
     * sinon a essayer des mots de passe a volonte pour verrouiller le compte.
     */
    @PostMapping("/password")
    public AuthResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        rateLimiter.checkPasswordChange(AuthenticatedUser.idOf(jwt));
        return accountService.changePassword(AuthenticatedUser.idOf(jwt), request);
    }

    /**
     * Supprime definitivement le compte et toutes ses donnees.
     *
     * <p>Un corps de requete sur un {@code DELETE} est inhabituel mais legitime,
     * et c'est le moindre mal : mettre le mot de passe dans l'URL le ferait
     * atterrir dans les journaux du serveur et du proxy.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody DeleteAccountRequest request) {
        rateLimiter.checkPasswordChange(AuthenticatedUser.idOf(jwt));
        accountService.delete(AuthenticatedUser.idOf(jwt), request.password());
    }
}
