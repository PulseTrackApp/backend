package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.error.InvalidVerificationCodeException;
import com.pulsetrack.backend.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmation de l'adresse email par code envoye a cette adresse.
 *
 * <p>Le but n'est pas de proteger un acces — le mot de passe s'en charge — mais
 * de garantir que l'adresse existe et appartient bien a la personne. Sans cela,
 * une faute de frappe a l'inscription rend la reinitialisation de mot de passe
 * inoperante, ce qu'on decouvre le jour ou il est trop tard.
 *
 * <p>Memes principes que {@link PasswordResetService} : la demande ne revele
 * jamais si l'adresse existe, un seul code vaut a la fois, et un code ne sert
 * qu'une fois.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final VerificationCodeSender sender;
    private final Duration ttl;

    public EmailVerificationService(UserRepository users,
                                    EmailVerificationTokenRepository tokens,
                                    VerificationCodeSender sender,
                                    SecurityProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.sender = sender;
        this.ttl = properties.emailVerification().ttl();
    }

    /**
     * Emet un code et l'envoie, si l'adresse designe un compte a verifier.
     *
     * <p>Ne signale jamais l'inverse : une adresse inconnue, et une adresse deja
     * confirmee, produisent la meme reponse qu'un envoi reel. Distinguer les
     * trois cas ferait de cet endpoint un moyen de savoir qui possede un compte.
     */
    @Transactional
    public void requestCode(String rawEmail) {
        Optional<User> account = users.findByEmail(AuthService.normalizeEmail(rawEmail));
        if (account.isEmpty()) {
            // Journalise sans l'adresse : la trace ne doit pas devenir la liste
            // des adresses testees par un curieux.
            log.info("Demande de verification pour une adresse inconnue, ignoree.");
            return;
        }

        User user = account.get();
        if (user.isEmailVerified()) {
            log.info("Compte {} deja verifie : aucun code emis.", user.getId());
            return;
        }
        sendCodeTo(user);
    }

    /**
     * Emet un code pour un compte connu, sans passer par l'adresse.
     *
     * <p>Appele a l'inscription, ou le compte vient d'etre cree et n'a par
     * definition pas encore de raison d'etre cherche.
     */
    @Transactional
    public void sendCodeTo(User user) {
        Instant now = Instant.now();
        tokens.deleteExpired(now);
        invalidatePending(user.getId(), now);

        String code = PasswordResetCodes.generate();
        tokens.save(new EmailVerificationToken(
                user.getId(), RefreshTokenService.hash(code), now, now.plus(ttl)));

        sender.send(user.getEmail(), code);
    }

    /**
     * Consomme un code et marque l'adresse comme verifiee.
     *
     * <p>Le code suffit a designer le compte : il est tire au hasard sur huit
     * caracteres, indexe, et l'endpoint est plafonne. Demander l'adresse en plus
     * n'ajouterait aucune securite et donnerait une occasion de plus de se
     * tromper.
     *
     * @throws InvalidVerificationCodeException si le code est inconnu, expire ou
     *                                          deja utilise — les trois cas
     *                                          rendus indistinguables cote client
     */
    @Transactional
    public void verify(String rawCode) {
        Instant now = Instant.now();
        String code = PasswordResetCodes.normalize(rawCode);

        EmailVerificationToken token = tokens.findByTokenHash(RefreshTokenService.hash(code))
                .orElseThrow(() -> new InvalidVerificationCodeException("Code inconnu."));

        if (token.isUsed() || token.hasExpiredAt(now)) {
            throw new InvalidVerificationCodeException("Code perime ou deja utilise.");
        }

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidVerificationCodeException("Compte introuvable."));

        user.markEmailVerified();
        token.useAt(now);
        log.info("Adresse verifiee pour le compte {}", user.getId());
    }

    private void invalidatePending(UUID userId, Instant now) {
        List<EmailVerificationToken> pending = tokens.findByUserIdAndUsedAtIsNull(userId);
        pending.forEach(token -> token.useAt(now));
    }
}
