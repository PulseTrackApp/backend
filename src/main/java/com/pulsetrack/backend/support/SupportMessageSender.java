package com.pulsetrack.backend.support;

/**
 * Acheminement d'un message de support.
 *
 * <p>Interface plutot qu'appel direct au SMTP, comme pour les codes de
 * reinitialisation : sans messagerie configuree, l'application doit demarrer et
 * le formulaire doit continuer de repondre. C'est
 * {@link LoggingSupportMessageSender} qui prend alors le relais.
 */
public interface SupportMessageSender {

    void send(SupportMessage message);
}
