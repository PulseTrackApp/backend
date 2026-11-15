package com.pulsetrack.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Envoi reel par courriel, via le SMTP configure.
 *
 * <p>L'envoi est asynchrone : un SMTP lent bloquerait sinon la requete HTTP
 * jusqu'a sa reponse, et l'utilisateur resterait devant un formulaire fige apres
 * avoir ecrit son message.
 *
 * <p><strong>L'expediteur reste l'adresse de service, pas celle de
 * l'utilisateur.</strong> Poster un courriel en se faisant passer pour quelqu'un
 * d'autre le fait classer en indesirable par tout serveur qui verifie SPF et
 * DKIM. L'adresse de l'utilisateur va en repondre-a : meme confort, sans le
 * rejet.
 */
public class MailSupportMessageSender implements SupportMessageSender {

    private static final Logger log = LoggerFactory.getLogger(MailSupportMessageSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String recipient;

    public MailSupportMessageSender(JavaMailSender mailSender, String from, String recipient) {
        this.mailSender = mailSender;
        this.from = from;
        this.recipient = recipient;
    }

    @Override
    @Async
    public void send(SupportMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(recipient);
        if (message.senderEmail() != null && !message.senderEmail().isBlank()) {
            mail.setReplyTo(message.senderEmail());
        }
        mail.setSubject("GymFlow - " + message.subject());
        mail.setText(body(message));

        try {
            mailSender.send(mail);
        } catch (RuntimeException exception) {
            // L'envoi est asynchrone : personne n'attend cette exception, et la
            // laisser remonter la ferait disparaitre dans le pool de threads.
            // L'utilisateur, lui, a deja recu sa confirmation — c'est le prix de
            // ne pas le faire patienter derriere un SMTP.
            log.error("Message de support non remis pour le compte {}", message.userId(), exception);
        }
    }

    private String body(SupportMessage message) {
        return """
                %s

                --
                Compte : %s
                Identifiant : %s
                Application : %s
                Plateforme : %s
                """.formatted(
                message.body(),
                message.senderEmail() == null ? "inconnu" : message.senderEmail(),
                message.userId(),
                message.appVersion() == null ? "non annoncee" : message.appVersion(),
                message.platform() == null ? "non annoncee" : message.platform());
    }
}
