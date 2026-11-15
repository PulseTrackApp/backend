package com.pulsetrack.backend.support;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.error.RateLimitedException;
import com.pulsetrack.backend.common.ratelimit.FixedWindowRateLimiter;
import com.pulsetrack.backend.user.User;
import com.pulsetrack.backend.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Achemine les messages ecrits depuis l'ecran d'aide.
 *
 * <p><strong>L'adresse de destination ne quitte jamais le serveur.</strong>
 * L'application envoie un sujet et un texte, rien de plus : elle ignore a qui
 * cela arrive. C'est ce qui remplace l'affichage d'une adresse de courriel dans
 * l'ecran d'aide, ou n'importe qui pouvait la relever.
 *
 * <p>L'identite de l'emetteur est lue sur le compte, jamais sur le corps de la
 * requete : sans cela, le formulaire permettrait d'ecrire au nom de quelqu'un
 * d'autre.
 */
@Service
public class SupportService {

    private static final String RATE_KEY = "support:user:";

    private final SupportMessageSender sender;
    private final UserRepository users;
    private final FixedWindowRateLimiter limiter;
    private final SupportProperties properties;

    public SupportService(SupportMessageSender sender,
                          UserRepository users,
                          FixedWindowRateLimiter limiter,
                          SupportProperties properties) {
        this.sender = sender;
        this.users = users;
        this.limiter = limiter;
        this.properties = properties;
    }

    /**
     * @throws RateLimitedException quand le compte a epuise son quota de
     *                              messages sur la fenetre courante
     */
    @Transactional(readOnly = true)
    public void send(UUID userId, String subject, String body, String appVersion, String platform) {
        Optional<Duration> retryAfter = limiter.tryConsume(
                RATE_KEY + userId, properties.maxMessages(), properties.window());
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(
                    "Trop de messages envoyés. Réessaie un peu plus tard.", retryAfter.get());
        }

        String email = users.findById(userId).map(User::getEmail).orElse(null);
        sender.send(new SupportMessage(
                userId, email, appVersion, platform, subject.trim(), body.trim()));
    }
}
