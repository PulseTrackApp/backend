package com.pulsetrack.backend.user;

/**
 * Achemine le code de reinitialisation jusqu'a l'utilisateur.
 *
 * <p>Abstrait pour la meme raison que {@code PushSender} : sans SMTP configure,
 * l'application doit demarrer et le parcours rester testable.
 */
public interface ResetCodeSender {

    /**
     * Envoie le code. Ne leve jamais : l'utilisateur a deja recu sa reponse
     * HTTP quand cette methode s'execute, et un echec d'acheminement ne doit
     * pas remonter — il ne pourrait plus etre signale a personne.
     */
    void send(String email, String code);
}
