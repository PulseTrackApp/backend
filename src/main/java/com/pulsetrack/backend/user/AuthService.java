package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.InvalidRefreshTokenException;
import com.pulsetrack.backend.profile.UserProfileRepository;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.LoginRequest;
import com.pulsetrack.backend.user.dto.RefreshRequest;
import com.pulsetrack.backend.user.dto.RegisterRequest;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription, connexion, renouvellement et deconnexion.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users,
                       UserProfileRepository profiles,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
    }

    /**
     * Cree un compte et retourne un jeton, pour que l'onboarding enchaine
     * directement sur le questionnaire de profil sans seconde requete.
     *
     * @throws ConflictException si l'email est deja utilise
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Un compte existe deja pour cet email.");
        }

        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Instant.now()));
        return toResponse(user, false);
    }

    /**
     * @throws BadCredentialsException si l'email est inconnu ou le mot de passe faux
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }

        return toResponse(user, profiles.existsByUserId(user.getId()));
    }

    /**
     * Echange un jeton de renouvellement contre une session neuve, sans
     * redemander le mot de passe.
     *
     * <p>Pas de {@code @Transactional} ici, a dessein : {@code consume} doit
     * pouvoir valider la revocation d'un jeton rejoue tout en refusant la
     * requete, ce qu'une transaction englobante annulerait.
     *
     * @throws InvalidRefreshTokenException si le jeton est inconnu, expire ou
     *                                      deja consomme
     */
    public AuthResponse refresh(RefreshRequest request) {
        UUID userId = refreshTokens.consume(request.refreshToken());

        // Le compte a pu etre supprime entre l'emission et le renouvellement :
        // le jeton reste valide en apparence, mais ne designe plus personne.
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Compte introuvable."));

        return toResponse(user, profiles.existsByUserId(user.getId()));
    }

    /**
     * Ferme la session portee par le jeton presente. Idempotent.
     *
     * @param userId compte authentifie par le jeton d'acces
     */
    public void logout(RefreshRequest request, UUID userId) {
        refreshTokens.revoke(request.refreshToken(), userId);
    }

    private AuthResponse toResponse(User user, boolean profileCompleted) {
        RefreshTokenService.IssuedToken refreshToken = refreshTokens.issueFor(user.getId());
        return new AuthResponse(
                tokenService.generateAccessToken(user),
                "Bearer",
                tokenService.accessTokenTtlSeconds(),
                refreshToken.value(),
                refreshToken.expiresInSeconds(),
                user.getId(),
                user.getEmail(),
                profileCompleted);
    }

    /**
     * Minuscules et espaces retires : sans cela, {@code Nico@Mail.com} et
     * {@code nico@mail.com} creeraient deux comptes distincts.
     *
     * <p>Partage avec {@link AuthRateLimiter}, qui compte les tentatives par
     * compte vise : si les deux ne normalisaient pas de la meme facon, changer
     * la casse de l'email suffirait a repartir avec un quota neuf.
     */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
