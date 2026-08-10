package com.pulsetrack.backend.user;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.LoginRequest;
import com.pulsetrack.backend.user.dto.RefreshRequest;
import com.pulsetrack.backend.user.dto.RegisterRequest;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entree et sortie de session.
 *
 * <p>Trois de ces endpoints sont ouverts sans jeton — inscription, connexion,
 * renouvellement — et sont declares un par un dans {@code SecurityConfig}. La
 * deconnexion, elle, exige un jeton d'acces valide : c'est ce qui garantit que
 * la session fermee est bien celle de l'appelant.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Le quota est consomme avant d'appeler le service : sans lui, creer des
     * comptes en boucle remplit la base et le hachage bcrypt de chaque mot de
     * passe sature le processeur.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest) {
        rateLimiter.checkRegister(clientIp(httpRequest));
        return authService.register(request);
    }

    /**
     * Le quota est verifie avant toute verification du mot de passe : c'est le
     * seul ordre qui protege reellement, puisque c'est le bcrypt de la
     * verification qui coute cher.
     */
    @PostMapping("/login")
    @SecurityRequirements
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest) {
        rateLimiter.checkLogin(clientIp(httpRequest), request.email());
        AuthResponse response = authService.login(request);
        rateLimiter.loginSucceeded(request.email());
        return response;
    }

    /**
     * Ouvert sans jeton, par nature : on vient justement le renouveler parce que
     * le precedent a expire. C'est la possession du jeton de renouvellement qui
     * authentifie l'appel.
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * Revoque la session portee par le jeton presente. Repond 204 meme si le
     * jeton etait deja revoque : la deconnexion est un resultat, pas une action
     * a reussir.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshRequest request) {
        authService.logout(request, AuthenticatedUser.idOf(jwt));
    }

    /**
     * Adresse de l'appelant, telle que vue apres traitement des en-tetes du
     * proxy ({@code server.forward-headers-strategy} en production). Sans ce
     * reglage, toutes les requetes semblent venir du proxy et partagent alors un
     * seul et meme quota.
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "inconnu" : remoteAddr;
    }
}
