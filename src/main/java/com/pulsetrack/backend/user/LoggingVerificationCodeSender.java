package com.pulsetrack.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repli sans SMTP : le code de confirmation part dans les journaux.
 *
 * <p>Permet d'eprouver tout le parcours sans serveur de messagerie. Acceptable
 * sur un poste de developpement, inacceptable ailleurs — d'ou l'avertissement.
 */
public class LoggingVerificationCodeSender implements VerificationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationCodeSender.class);

    @Override
    public void send(String email, String code) {
        log.warn("Envoi de courriel désactivé : code de vérification pour {} = {}", email, code);
    }
}
