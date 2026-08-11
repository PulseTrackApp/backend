package com.pulsetrack.backend.user;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Envoi reel par courriel, via le SMTP configure.
 *
 * <p>L'envoi est <strong>asynchrone</strong>, pour deux raisons. La
 * disponibilite d'abord : un SMTP lent bloquerait la requete HTTP jusqu'a sa
 * reponse. La confidentialite ensuite : si l'endpoint repondait vite pour une
 * adresse inconnue et lentement pour une adresse existante, cet ecart de duree
 * suffirait a enumerer les comptes — exactement ce que la reponse uniforme
 * cherche a empecher.
 */
public class MailResetCodeSender implements ResetCodeSender {

    private static final Logger log = LoggerFactory.getLogger(MailResetCodeSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration validity;

    public MailResetCodeSender(JavaMailSender mailSender, String from, Duration validity) {
        this.mailSender = mailSender;
        this.from = from;
        this.validity = validity;
    }

    @Override
    @Async
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("GymFlow - code de reinitialisation");
        message.setText(body(code));

        try {
            mailSender.send(message);
            log.info("Code de reinitialisation envoye.");
        } catch (Exception ex) {
            // Volontairement large : un SMTP echoue de mille manieres, et
            // aucune ne peut plus etre signalee a l'appelant. L'adresse n'est
            // pas journalisee — elle designerait un compte existant.
            log.error("Envoi du code de reinitialisation impossible", ex);
        }
    }

    /**
     * Message sobre et sans lien cliquable : un courriel de reinitialisation
     * truffe de liens ressemble a s'y meprendre a un hameconnage, et apprend a
     * l'utilisateur a cliquer sans reflechir.
     */
    private String body(String code) {
        return """
                Bonjour,

                Voici votre code de reinitialisation GymFlow :

                    %s

                Saisissez-le dans l'application pour choisir un nouveau mot de passe.
                Il est valable %d minutes et ne peut servir qu'une seule fois.

                Si vous n'avez rien demande, ignorez ce message : votre mot de
                passe reste inchange.
                """.formatted(code, validity.toMinutes());
    }
}
