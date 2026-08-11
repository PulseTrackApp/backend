package com.pulsetrack.backend.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envoi de courriel applicatif.
 *
 * <p>Distinct de {@code spring.mail.*}, qui configure le transport SMTP : ici
 * on decrit ce que l'application en fait.
 *
 * @param enabled coupe l'envoi. Laisse a {@code false}, la demande de
 *                reinitialisation reste acceptee et le code est journalise au
 *                lieu d'etre envoye — c'est ce qui permet de developper et de
 *                tester sans SMTP.
 * @param from    adresse d'expedition ; avec Gmail elle doit correspondre au
 *                compte authentifie, sinon le serveur reecrit ou rejette
 */
@ConfigurationProperties(prefix = "pulsetrack.mail")
public record MailProperties(boolean enabled, String from) {
}
