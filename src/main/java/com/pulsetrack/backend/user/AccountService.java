package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.access.ModuleAccessService;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.user.dto.AuthResponse;
import com.pulsetrack.backend.user.dto.ChangePasswordRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ce qu'un utilisateur peut faire de son propre compte : changer son mot de
 * passe, et le supprimer.
 *
 * <p>Les deux operations exigent le mot de passe actuel, alors meme que
 * l'appelant presente deja un jeton valide. C'est delibere : un telephone laisse
 * deverrouille sur une table ne doit pas suffire a s'approprier un compte ni a
 * effacer des annees de seances.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final ModuleAccessService moduleAccess;
    private final AuthService authService;

    public AccountService(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokens,
                          ModuleAccessService moduleAccess,
                          AuthService authService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.moduleAccess = moduleAccess;
        this.authService = authService;
    }

    /**
     * Remplace le mot de passe et repart sur une session neuve.
     *
     * <p>Toutes les sessions ouvertes tombent, comme lors d'une
     * reinitialisation : changer de mot de passe apres avoir prete son telephone
     * n'aurait aucun effet si la session ouverte sur ce telephone survivait. Une
     * session est ensuite rendue a l'appareil qui vient de faire la demande,
     * faute de quoi l'utilisateur se retrouverait deconnecte pour avoir suivi la
     * procedure.
     *
     * <p>Le jeton d'acces des autres appareils reste valable jusqu'a son
     * expiration — il est signe, aucun serveur ne peut le rappeler. C'est le
     * renouvellement qui leur sera refuse. Meme limite que la reinitialisation.
     *
     * @throws BusinessRuleException si le mot de passe actuel est faux, ou si le
     *                               nouveau lui est identique
     */
    @Transactional
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = require(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            // Sans ce refus, l'utilisateur croirait avoir change quelque chose
            // alors que rien n'a bouge, et se croirait protege a tort.
            throw new BusinessRuleException("Le nouveau mot de passe doit differer de l'ancien.");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeAllFor(userId);
        log.info("Mot de passe change, sessions revoquees pour le compte {}", userId);

        return authService.openSessionFor(user);
    }

    /**
     * Supprime le compte et, par cascade en base, tout ce qui s'y rattache :
     * profil, seances, traces GPS, pesees, objectifs, reglages de l'assistant,
     * echanges, jetons d'appareil et de session.
     *
     * <p>Definitif et sans corbeille. L'export des donnees personnelles
     * ({@code GET /api/v1/me/export}) existe justement pour etre propose avant.
     *
     * @throws BusinessRuleException si le mot de passe est faux, ou si le compte
     *                               est administrateur — se supprimer soi-meme
     *                               fermerait l'espace d'administration a tout
     *                               le monde, y compris pour reparer la betise.
     *                               Il faut d'abord se faire retrograder par un
     *                               autre administrateur
     */
    @Transactional
    public void delete(UUID userId, String password) {
        User user = require(userId);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("Le mot de passe est incorrect.");
        }
        if (user.isAdmin()) {
            throw new BusinessRuleException(
                    "Un administrateur ne peut pas supprimer son propre compte.");
        }

        users.delete(user);
        moduleAccess.evict(userId);
        log.info("Compte {} supprimé à la demande de son propriétaire", userId);
    }

    private User require(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));
    }
}
