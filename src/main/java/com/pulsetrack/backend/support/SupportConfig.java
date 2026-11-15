package com.pulsetrack.backend.support;

import com.pulsetrack.backend.user.MailProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Choisit l'acheminement des messages de support.
 *
 * <p>Le courriel reel demande trois choses reunies : la messagerie activee, une
 * adresse d'expedition, et un destinataire. Il en manque une et l'application
 * demarre quand meme, en journalisant les messages — un formulaire d'aide qui
 * empeche le serveur de demarrer serait un remede pire que le mal.
 *
 * <p>Le {@link JavaMailSender} est demande par {@link ObjectProvider} et non
 * directement : sans transport SMTP configure, ce bean n'existe pas, et l'exiger
 * rendrait la configuration impossible a creer sur l'installation meme qu'on
 * cherche a supporter.
 */
@Configuration
public class SupportConfig {

    private static final Logger log = LoggerFactory.getLogger(SupportConfig.class);

    @Bean
    SupportMessageSender supportMessageSender(SupportProperties support,
                                              MailProperties mail,
                                              ObjectProvider<JavaMailSender> mailSender) {
        if (!mail.enabled() || mail.from() == null || mail.from().isBlank()) {
            log.info("Messages de support journalisés : la messagerie est désactivée.");
            return new LoggingSupportMessageSender();
        }
        if (!support.hasRecipient()) {
            log.warn("Messages de support journalisés : pulsetrack.support.recipient n'est pas renseigné.");
            return new LoggingSupportMessageSender();
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Messages de support journalisés : aucun transport SMTP configuré (spring.mail.host).");
            return new LoggingSupportMessageSender();
        }

        log.info("Messages de support acheminés par courriel.");
        return new MailSupportMessageSender(sender, mail.from(), support.recipient());
    }
}
