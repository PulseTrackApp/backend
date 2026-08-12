package com.pulsetrack.backend.user;

/**
 * Achemine le code de confirmation d'adresse jusqu'a l'utilisateur.
 *
 * <p>Interface distincte de {@link ResetCodeSender} bien que les deux envoient
 * un code par courriel : le message n'est pas le meme, et surtout on veut
 * pouvoir couper l'un sans couper l'autre. Confondre les deux ferait qu'un
 * courriel de bienvenue mal configure emporterait la reinitialisation de mot de
 * passe avec lui.
 */
public interface VerificationCodeSender {

    /**
     * Envoie le code. Ne leve jamais : l'appelant a deja repondu au client quand
     * cette methode s'execute.
     */
    void send(String email, String code);
}
