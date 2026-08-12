package com.pulsetrack.backend.common.error;

/**
 * Session refusee a un compte dont l'adresse n'est pas confirmee. Traduit en
 * HTTP 403.
 *
 * <p>N'existe que si {@code pulsetrack.security.email-verification.required}
 * est actif. Distinct d'un refus d'identifiants : le mot de passe etait bon, et
 * le client doit conduire l'utilisateur vers la saisie du code plutot que de lui
 * faire retaper son mot de passe indefiniment.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
