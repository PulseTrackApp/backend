package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.Locale;

import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.profile.UserProfileRepository;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.LoginRequest;
import com.pulsetrack.backend.user.dto.RegisterRequest;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription et connexion.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users,
                       UserProfileRepository profiles,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * Cree un compte et retourne un jeton, pour que l'onboarding enchaine
     * directement sur le questionnaire de profil sans seconde requete.
     *
     * @throws ConflictException si l'email est deja utilise
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Un compte existe deja pour cet email.");
        }

        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Instant.now()));
        return toResponse(user, false);
    }

    /**
     * @throws BadCredentialsException si l'email est inconnu ou le mot de passe faux
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }

        return toResponse(user, profiles.existsByUserId(user.getId()));
    }

    private AuthResponse toResponse(User user, boolean profileCompleted) {
        return new AuthResponse(
                tokenService.generateAccessToken(user),
                "Bearer",
                tokenService.accessTokenTtlSeconds(),
                user.getId(),
                user.getEmail(),
                profileCompleted);
    }

    /**
     * Minuscules et espaces retires : sans cela, {@code Nico@Mail.com} et
     * {@code nico@mail.com} creeraient deux comptes distincts.
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
