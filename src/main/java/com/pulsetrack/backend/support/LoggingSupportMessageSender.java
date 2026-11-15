package com.pulsetrack.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repli sans messagerie : le message part dans les journaux.
 *
 * <p>Ce n'est pas seulement un pis-aller de developpement. Tant qu'aucun SMTP
 * n'est configure en production, un message ecrit par un utilisateur reste
 * lisible dans les journaux du serveur plutot que d'etre perdu — et le
 * formulaire, lui, ne tombe pas en panne.
 */
public class LoggingSupportMessageSender implements SupportMessageSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSupportMessageSender.class);

    @Override
    public void send(SupportMessage message) {
        log.info("Message de support du compte {} ({}) - sujet : {}{}{}",
                message.userId(), message.senderEmail(), message.subject(),
                System.lineSeparator(), message.body());
    }
}
