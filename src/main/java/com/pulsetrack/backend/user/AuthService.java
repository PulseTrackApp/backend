package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.pulsetrack.backend.access.AccessProperties;
import com.pulsetrack.backend.access.ModuleAccessService;
import com.pulsetrack.backend.common.error.ConflictException;
import com.pulsetrack.backend.common.error.EmailNotVerifiedException;
import com.pulsetrack.backend.common.error.InvalidRefreshTokenException;
import com.pulsetrack.backend.config.SecurityProperties;
import com.pulsetrack.backend.profile.UserProfileRepository;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.LoginRequest;
import com.pulsetrack.backend.user.dto.RefreshRequest;
import com.pulsetrack.backend.user.dto.RegisterRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription, connexion, renouvellement et deconnexion.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final ModuleAccessService moduleAccess;
    private final EmailVerificationService emailVerification;
    private final AccessProperties accessProperties;
    private final boolean verifiedEmailRequired;

    public AuthService(UserRepository users,
                       UserProfileRepository profiles,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       RefreshTokenService refreshTokens,
                       ModuleAccessService moduleAccess,
                       EmailVerificationService emailVerification,
                       AccessProperties accessProperties,
                       SecurityProperties securityProperties) {
        this.users = users;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.moduleAccess = moduleAccess;
        this.emailVerification = emailVerification;
        this.accessProperties = accessProperties;
        this.verifiedEmailRequired = securityProperties.emailVerification().required();
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
        promoteIfConfiguredAdmin(user);
        moduleAccess.grantDefaults(user.getId());

        // Le code part des maintenant, sans que l'inscription en depende : elle
        // reussit meme si le courriel n'arrive jamais. Faire l'inverse
        // laisserait un compte a moitie cree sur une panne de SMTP.
        emailVerification.sendCodeTo(user);

        return toResponse(user, false);
    }

    /**
     * Promeut le compte a l'inscription s'il porte l'adresse declaree
     * administrateur.
     *
     * <p>Sans ce controle ici, la promotion ne pourrait avoir lieu qu'au
     * demarrage, et le tout premier administrateur — dont le compte n'existe pas
     * encore quand la variable est posee — devrait redemarrer l'application
     * apres s'etre inscrit pour obtenir ses droits. Le cas nominal deviendrait
     * une manipulation serveur.
     */
    private void promoteIfConfiguredAdmin(User user) {
        if (!accessProperties.hasAdminEmail()) {
            return;
        }
        if (!user.getEmail().equals(normalizeEmail(accessProperties.adminEmail()))) {
            return;
        }
        user.changeRole(Role.ADMIN);
        log.info("Compte {} promu administrateur a l'inscription.", user.getId());
    }

    /**
     * @throws BadCredentialsException   si l'email est inconnu ou le mot de passe faux
     * @throws EmailNotVerifiedException si la verification est exigee et que
     *                                   l'adresse n'est pas confirmee
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }
        // Apres la verification du mot de passe, et jamais avant : annoncer
        // « adresse non verifiee » a qui ne connait pas le mot de passe
        // revelerait l'existence du compte.
        requireVerifiedEmail(user);

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

        // Le renouvellement est controle comme la connexion, sinon la session
        // obtenue a l'inscription se prolongerait indefiniment et la
        // verification ne serait jamais exigee de personne.
        requireVerifiedEmail(user);

        return toResponse(user, profiles.existsByUserId(user.getId()));
    }

    /**
     * Ouvre une session pour un compte dont l'identite vient d'etre etablie
     * autrement que par le couple email/mot de passe.
     *
     * <p>Sert au changement de mot de passe, qui coupe toutes les sessions et
     * doit en rendre une neuve a l'appareil qui vient de faire la demande —
     * sans quoi l'utilisateur serait deconnecte pour avoir suivi la procedure.
     */
    @Transactional
    public AuthResponse openSessionFor(User user) {
        return toResponse(user, profiles.existsByUserId(user.getId()));
    }

    /**
     * @throws EmailNotVerifiedException si la verification est exigee par la
     *                                   configuration et que l'adresse du compte
     *                                   n'est pas confirmee
     */
    private void requireVerifiedEmail(User user) {
        if (verifiedEmailRequired && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException(
                    "Confirmez votre adresse email avec le code recu par courriel.");
        }
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
                profileCompleted,
                user.isEmailVerified(),
                user.getRole());
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
