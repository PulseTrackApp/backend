package com.pulsetrack.backend.user;

import java.time.Duration;
import java.util.UUID;

import com.pulsetrack.backend.common.error.RateLimitedException;
import com.pulsetrack.backend.common.ratelimit.FixedWindowRateLimiter;
import com.pulsetrack.backend.config.SecurityProperties;

import org.springframework.stereotype.Component;

/**
 * Plafonne les tentatives sur les deux endpoints ouverts.
 *
 * <p>La connexion est limitee sur <em>deux</em> cles a la fois : l'adresse IP,
 * qui arrete celui qui essaie mille mots de passe sur un compte, et l'email
 * vise, qui arrete celui qui repartit ses essais sur mille adresses. Aucune des
 * deux ne suffit seule.
 *
 * <p>Une connexion reussie efface le compteur de l'email : un utilisateur qui
 * s'est trompe trois fois puis a retrouve son mot de passe ne doit pas rester
 * bloque pour autant. Le compteur d'IP, lui, n'est jamais efface : un attaquant
 * disposant d'un compte valide pourrait sinon remettre son quota a zero a
 * volonte.
 */
@Component
public class AuthRateLimiter {

    private static final String LOGIN_BY_IP = "login:ip:";
    private static final String LOGIN_BY_EMAIL = "login:email:";
    private static final String REGISTER_BY_IP = "register:ip:";
    private static final String RESET_BY_IP = "reset:ip:";
    private static final String RESET_BY_EMAIL = "reset:email:";
    private static final String VERIFY_BY_IP = "verify:ip:";
    private static final String VERIFY_BY_EMAIL = "verify:email:";
    private static final String PASSWORD_CHANGE_BY_USER = "password-change:user:";

    private final FixedWindowRateLimiter limiter;
    private final SecurityProperties.RateLimit policies;

    public AuthRateLimiter(FixedWindowRateLimiter limiter, SecurityProperties properties) {
        this.limiter = limiter;
        this.policies = properties.rateLimit();
    }

    /**
     * @throws RateLimitedException si l'IP ou le compte vise a epuise son quota
     */
    public void checkLogin(String clientIp, String email) {
        SecurityProperties.RateLimit.Policy policy = policies.login();
        consume(LOGIN_BY_IP + clientIp, policy);
        consume(LOGIN_BY_EMAIL + AuthService.normalizeEmail(email), policy);
    }

    /**
     * @throws RateLimitedException si l'IP a epuise son quota de creations
     */
    public void checkRegister(String clientIp) {
        consume(REGISTER_BY_IP + clientIp, policies.register());
    }

    /**
     * Demande d'un code de reinitialisation.
     *
     * <p>Plafonne par adresse visee autant que par IP : sans la premiere cle,
     * l'endpoint permettrait d'inonder de courriels la boite de quelqu'un
     * d'autre, chaque envoi partant d'une adresse differente.
     *
     * @throws RateLimitedException si l'IP ou l'adresse visee a epuise son quota
     */
    public void checkPasswordResetRequest(String clientIp, String email) {
        SecurityProperties.RateLimit.Policy policy = policies.passwordReset();
        consume(RESET_BY_IP + clientIp, policy);
        consume(RESET_BY_EMAIL + AuthService.normalizeEmail(email), policy);
    }

    /**
     * Essai d'un code de reinitialisation.
     *
     * <p>Seule l'IP est connue ici — le code ne designe personne tant qu'il
     * n'est pas valide. C'est ce plafond qui met une recherche exhaustive hors
     * de portee, le code ne faisant que huit caracteres.
     *
     * @throws RateLimitedException si l'IP a epuise son quota d'essais
     */
    public void checkPasswordResetAttempt(String clientIp) {
        consume(RESET_BY_IP + clientIp, policies.passwordReset());
    }

    /**
     * Demande d'un code de confirmation d'adresse.
     *
     * <p>Plafonne par adresse visee autant que par IP, comme la
     * reinitialisation : sans la premiere cle, l'endpoint permettrait d'inonder
     * de courriels la boite de quelqu'un d'autre.
     *
     * @throws RateLimitedException si l'IP ou l'adresse visee a epuise son quota
     */
    public void checkEmailVerificationRequest(String clientIp, String email) {
        SecurityProperties.RateLimit.Policy policy = policies.emailVerification();
        consume(VERIFY_BY_IP + clientIp, policy);
        consume(VERIFY_BY_EMAIL + AuthService.normalizeEmail(email), policy);
    }

    /**
     * Essai d'un code de confirmation.
     *
     * <p>Seule l'IP est connue : le code ne designe personne tant qu'il n'est
     * pas valide. C'est ce plafond qui met sa recherche exhaustive hors de
     * portee.
     *
     * @throws RateLimitedException si l'IP a epuise son quota d'essais
     */
    public void checkEmailVerificationAttempt(String clientIp) {
        consume(VERIFY_BY_IP + clientIp, policies.emailVerification());
    }

    /**
     * Changement de mot de passe et suppression de compte, tous deux gardes par
     * le mot de passe actuel.
     *
     * <p>Cle sur le compte et non sur l'IP : l'appelant est authentifie, c'est
     * son jeton qu'on plafonne. Sans ce controle, un jeton vole servirait a
     * essayer des mots de passe a volonte — et chaque essai coute un bcrypt sur
     * un serveur a deux processeurs.
     *
     * @throws RateLimitedException si le compte a epuise son quota d'essais
     */
    public void checkPasswordChange(UUID userId) {
        consume(PASSWORD_CHANGE_BY_USER + userId, policies.login());
    }

    /** A appeler apres une authentification reussie. */
    public void loginSucceeded(String email) {
        limiter.reset(LOGIN_BY_EMAIL + AuthService.normalizeEmail(email));
    }

    private void consume(String key, SecurityProperties.RateLimit.Policy policy) {
        limiter.tryConsume(key, policy.maxAttempts(), policy.window())
                .ifPresent(retryAfter -> {
                    throw new RateLimitedException(message(retryAfter), retryAfter);
                });
    }

    /**
     * Le message ne dit ni quelle cle a saute, ni combien de tentatives
     * restaient : ce serait indiquer a un attaquant comment rester sous le
     * seuil.
     */
    private String message(Duration retryAfter) {
        long seconds = Math.max(1, retryAfter.toSeconds());
        return "Trop de tentatives. Reessayez dans %d secondes.".formatted(seconds);
    }
}
