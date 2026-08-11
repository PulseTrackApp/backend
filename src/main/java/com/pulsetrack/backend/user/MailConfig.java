package com.pulsetrack.backend.user;

import com.pulsetrack.backend.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Choisit l'acheminement du code de reinitialisation selon la configuration.
 *
 * <p>Les deux beans portent une condition sur la <em>meme</em> propriete, avec
 * des valeurs opposees : exactement un des deux existe, toujours. Meme patron
 * que {@code PushConfig}.
 *
 * <p>{@code @EnableAsync} sert a l'envoi hors du fil de la requete : sans lui,
 * l'annotation {@code @Async} du {@code MailResetCodeSender} serait ignoree et
 * l'envoi redeviendrait bloquant, avec l'ecart de duree qui trahit l'existence
 * d'un compte.
 */
@Configuration
@EnableAsync
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.mail", name = "enabled", havingValue = "true")
    ResetCodeSender mailResetCodeSender(JavaMailSender mailSender,
                                        MailProperties mail,
                                        SecurityProperties security) {
        if (mail.from() == null || mail.from().isBlank()) {
            throw new IllegalStateException(
                    "pulsetrack.mail.enabled=true exige pulsetrack.mail.from");
        }
        log.info("Envoi de courriel actif, expediteur {}", mail.from());
        return new MailResetCodeSender(mailSender, mail.from(), security.passwordReset().ttl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.mail", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    ResetCodeSender loggingResetCodeSender() {
        log.info("Envoi de courriel desactive : les codes de reinitialisation iront dans les journaux.");
        return new LoggingResetCodeSender();
    }
}
