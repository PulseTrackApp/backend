package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pulsetrack.backend.common.error.InvalidResetCodeException;
import com.pulsetrack.backend.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reinitialisation de mot de passe par code envoye a l'adresse du compte.
 *
 * <p>Trois principes gouvernent ce parcours :
 *
 * <p><strong>Ne rien reveler.</strong> La demande repond la meme chose, et en
 * un temps comparable, que l'adresse existe ou non. Sans cela, l'endpoint
 * devient un moyen commode de savoir qui possede un compte.
 *
 * <p><strong>Un seul code a la fois.</strong> Une nouvelle demande invalide les
 * precedentes : plusieurs codes valides simultanement multiplient les chances
 * d'un attaquant sans rendre service a l'utilisateur.
 *
 * <p><strong>Une reinitialisation coupe tout.</strong> Changer de mot de passe
 * revoque toutes les sessions ouvertes. C'est le sens meme de l'operation :
 * quelqu'un qui reinitialise soupconne souvent une intrusion, et laisser vivre
 * les sessions de l'intrus viderait le geste de son sens.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final ResetCodeSender sender;
    private final Duration ttl;

    public PasswordResetService(UserRepository users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokens,
                                ResetCodeSender sender,
                                SecurityProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.sender = sender;
        this.ttl = properties.passwordReset().ttl();
    }

    /**
     * Emet un code et l'envoie, si le compte existe.
     *
     * <p>Ne signale jamais l'inverse : cote appelant, une adresse inconnue est
     * indiscernable d'une adresse connue.
     */
    @Transactional
    public void requestCode(String rawEmail) {
        String email = AuthService.normalizeEmail(rawEmail);
        Instant now = Instant.now();
        tokens.deleteExpired(now);

        Optional<User> account = users.findByEmail(email);
        if (account.isEmpty()) {
            // Journalise sans l'adresse : la trace ne doit pas devenir la liste
            // des adresses testees par un curieux.
            log.info("Demande de réinitialisation pour une adresse inconnue, ignorée.");
            return;
        }

        User user = account.get();
        invalidatePending(user.getId(), now);

        String code = PasswordResetCodes.generate();
        tokens.save(new PasswordResetToken(
                user.getId(), RefreshTokenService.hash(code), now, now.plus(ttl)));

        sender.send(user.getEmail(), code);
    }

    /**
     * Consomme un code et remplace le mot de passe.
     *
     * @throws InvalidResetCodeException si le code est inconnu, expire ou deja
     *                                   utilise — les trois cas rendus
     *                                   indistinguables cote client
     */
    @Transactional
    public void resetPassword(String rawCode, String newPassword) {
        Instant now = Instant.now();
        String code = PasswordResetCodes.normalize(rawCode);

        PasswordResetToken token = tokens.findByTokenHash(RefreshTokenService.hash(code))
                .orElseThrow(() -> new InvalidResetCodeException("Code inconnu."));

        if (token.isUsed() || token.hasExpiredAt(now)) {
            throw new InvalidResetCodeException("Code périmé ou déjà utilisé.");
        }

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetCodeException("Compte introuvable."));

        user.changePassword(passwordEncoder.encode(newPassword));
        token.useAt(now);

        // Toutes les sessions tombent, y compris celle de l'utilisateur
        // legitime : il se reconnectera avec son nouveau mot de passe.
        refreshTokens.revokeAllFor(user.getId());
        log.info("Mot de passe reinitialise, sessions revoquees pour le compte {}", user.getId());
    }

    private void invalidatePending(java.util.UUID userId, Instant now) {
        List<PasswordResetToken> pending = tokens.findByUserIdAndUsedAtIsNull(userId);
        pending.forEach(token -> token.useAt(now));
    }
}
