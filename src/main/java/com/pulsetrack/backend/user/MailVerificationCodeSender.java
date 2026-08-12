package com.pulsetrack.backend.user;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

/**
 * Envoi reel du code de confirmation, via le SMTP configure.
 *
 * <p>Asynchrone comme {@link MailResetCodeSender} : l'inscription ne doit pas
 * attendre le serveur de messagerie pour repondre. Un SMTP lent rendrait la
 * creation de compte lente, alors que l'envoi n'est pas ce que l'utilisateur
 * attend a cet instant.
 */
public class MailVerificationCodeSender implements VerificationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(MailVerificationCodeSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final Duration validity;

    public MailVerificationCodeSender(JavaMailSender mailSender, String from, Duration validity) {
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
        message.setSubject("GymFlow - confirmez votre adresse");
        message.setText(body(code));

        try {
            mailSender.send(message);
            log.info("Code de verification envoye.");
        } catch (Exception ex) {
            // Volontairement large, et sans l'adresse : voir MailResetCodeSender.
            log.error("Envoi du code de verification impossible", ex);
        }
    }

    /**
     * Sans lien cliquable, comme le courriel de reinitialisation : un message
     * truffe de liens ressemble a s'y meprendre a un hameconnage.
     */
    private String body(String code) {
        return """
                Bonjour,

                Voici votre code de confirmation GymFlow :

                    %s

                Saisissez-le dans l'application pour confirmer votre adresse.
                Il est valable %d heures.

                Cette confirmation garantit que vous pourrez recuperer votre
                compte si vous oubliez votre mot de passe. Si vous n'avez pas
                cree de compte GymFlow, ignorez ce message.
                """.formatted(code, validity.toHours());
    }
}
