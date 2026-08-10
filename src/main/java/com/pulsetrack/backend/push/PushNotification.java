package com.pulsetrack.backend.push;

import java.util.Map;

/**
 * Notification a envoyer, independante du transport.
 *
 * <p>Ce type ne connait ni FCM ni aucun fournisseur : c'est ce qui permet de
 * remplacer l'implementation d'envoi sans toucher au code metier.
 *
 * @param title titre affiche
 * @param body  corps du message
 * @param data  couples cle/valeur transmis a l'application, pour ouvrir le bon
 *              ecran au clic (ex. {@code route=/body-checkin})
 */
public record PushNotification(String title, String body, Map<String, String> data) {

    public static PushNotification of(String title, String body) {
        return new PushNotification(title, body, Map.of());
    }

    public PushNotification withRoute(String route) {
        return new PushNotification(title, body, Map.of("route", route));
    }
}
