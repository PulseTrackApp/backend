package com.pulsetrack.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repli sans SMTP : le code part dans les journaux au lieu d'un courriel.
 *
 * <p>Permet de developper et d'eprouver tout le parcours sans configurer de
 * serveur de messagerie. Le code est journalise en clair, ce qui est acceptable
 * sur un poste de developpement et inacceptable ailleurs — d'ou
 * l'avertissement, et le fait que la production active toujours l'envoi reel.
 */
public class LoggingResetCodeSender implements ResetCodeSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingResetCodeSender.class);

    @Override
    public void send(String email, String code) {
        log.warn("Envoi de courriel desactive : code de reinitialisation pour {} = {}", email, code);
    }
}
