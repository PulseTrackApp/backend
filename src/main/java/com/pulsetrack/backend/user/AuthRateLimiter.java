package com.pulsetrack.backend.user;

import java.time.Duration;

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
