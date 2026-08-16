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
 * Choisit l'acheminement des codes envoyes par courriel selon la configuration.
 *
 * <p>Deux couples de beans — reinitialisation de mot de passe et confirmation
 * d'adresse — et dans chaque couple la meme propriete conditionne les deux
 * membres avec des valeurs opposees : exactement un des deux existe, toujours.
 * Meme patron que {@code PushConfig}.
 *
 * <p>{@code @EnableAsync} sert a l'envoi hors du fil de la requete : sans lui,
 * l'annotation {@code @Async} des expediteurs reels serait ignoree et l'envoi
 * redeviendrait bloquant, avec l'ecart de duree qui trahit l'existence d'un
 * compte.
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
        String from = requireFrom(mail);
        log.info("Envoi de courriel actif, expediteur {}", from);
        return new MailResetCodeSender(mailSender, from, security.passwordReset().ttl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.mail", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    ResetCodeSender loggingResetCodeSender() {
        log.info("Envoi de courriel désactivé : les codes de réinitialisation iront dans les journaux.");
        return new LoggingResetCodeSender();
    }

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.mail", name = "enabled", havingValue = "true")
    VerificationCodeSender mailVerificationCodeSender(JavaMailSender mailSender,
                                                      MailProperties mail,
                                                      SecurityProperties security) {
        return new MailVerificationCodeSender(mailSender, requireFrom(mail),
                security.emailVerification().ttl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.mail", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    VerificationCodeSender loggingVerificationCodeSender() {
        return new LoggingVerificationCodeSender();
    }

    /**
     * Le controle est repete dans chaque bean plutot que fait une seule fois :
     * rien ne garantit l'ordre de creation, et se reposer sur le voisin
     * laisserait passer une adresse d'expedition vide selon l'ordre du jour.
     */
    private static String requireFrom(MailProperties mail) {
        if (mail.from() == null || mail.from().isBlank()) {
            throw new IllegalStateException("pulsetrack.mail.enabled=true exige pulsetrack.mail.from");
        }
        return mail.from();
    }
}
