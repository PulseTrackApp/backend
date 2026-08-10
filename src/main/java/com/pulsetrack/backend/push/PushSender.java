package com.pulsetrack.backend.push;

/**
 * Envoi d'une notification a un appareil.
 *
 * <p>Interface plutot qu'appel direct a FCM : sans projet Firebase configure,
 * l'application doit demarrer et fonctionner normalement. C'est
 * {@link LoggingPushSender} qui prend alors le relais.
 */
public interface PushSender {

    /**
     * @param token        jeton d'enregistrement de l'appareil
     * @param notification contenu a delivrer
     * @return {@code false} si le jeton est rejete comme invalide par le
     *         fournisseur, auquel cas l'appelant doit le supprimer
     */
    boolean send(String token, PushNotification notification);
}
