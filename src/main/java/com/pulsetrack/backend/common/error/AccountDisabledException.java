package com.pulsetrack.backend.common.error;

/**
 * Acces refuse a un compte suspendu. Traduit en HTTP 403,
 * {@code type: .../account-disabled}.
 *
 * <p>Distinct d'un refus d'identifiants : le mot de passe etait bon. Le client
 * doit dire pourquoi l'acces est ferme plutot que faire retaper un mot de passe
 * indefiniment — le retaper ne changera rien, et l'utilisateur finirait par
 * croire l'avoir oublie.
 *
 * <p>Le message porte la raison saisie par l'administrateur quand il y en a une.
 * C'est voulu : une porte fermee sans explication n'a aucun recours possible.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
