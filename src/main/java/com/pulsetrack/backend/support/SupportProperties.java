package com.pulsetrack.backend.support;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ou aboutissent les messages ecrits depuis l'application, et a quel rythme.
 *
 * <p><strong>L'adresse ne descend jamais jusqu'au client.</strong> Elle vit ici
 * et nulle part ailleurs : l'application affiche un formulaire, le serveur seul
 * sait a qui la reponse est postee. Afficher l'adresse dans l'application
 * revenait a la publier — un binaire se decompile, et une adresse ainsi exposee
 * se retrouve dans les listes de diffusion non sollicitees sans qu'on puisse
 * jamais l'en retirer.
 *
 * @param recipient   destinataire des messages. Vide, l'endpoint refuse
 *                    poliment plutot que d'accepter un message que personne ne
 *                    lira
 * @param maxMessages messages tolerés par compte et par fenetre. Un formulaire
 *                    qui declenche un courriel est une machine a inonder une
 *                    boite aux lettres : le plafond n'est pas une precaution
 *                    theorique
 * @param window      duree de la fenetre de comptage
 */
@ConfigurationProperties(prefix = "pulsetrack.support")
public record SupportProperties(String recipient, int maxMessages, Duration window) {

    private static final int DEFAULT_MAX_MESSAGES = 3;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    /**
     * Rattrape un plafond absent ou absurde.
     *
     * <p>Sans cela, une propriete oubliee donne {@code maxMessages = 0} : le
     * formulaire refuserait alors <em>tous</em> les messages, en repondant
     * « trop de messages envoyes » au premier. Une panne muette de ce genre ne se
     * decouvre que le jour ou quelqu'un se plaint de ne pas pouvoir se plaindre.
     */
    public SupportProperties {
        if (maxMessages <= 0) {
            maxMessages = DEFAULT_MAX_MESSAGES;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = DEFAULT_WINDOW;
        }
    }

    public boolean hasRecipient() {
        return recipient != null && !recipient.isBlank();
    }
}
