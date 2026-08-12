package com.pulsetrack.backend.user;

/**
 * Expediteur de test : retient le code de confirmation au lieu de l'envoyer.
 *
 * <p>C'est le seul moyen d'eprouver le parcours de bout en bout — le code
 * n'apparait dans aucune reponse HTTP, precisement pour qu'on ne puisse pas le
 * recuperer sans acceder a la boite aux lettres.
 */
public class RecordingVerificationCodeSender implements VerificationCodeSender {

    private volatile String lastCode;
    private volatile String lastEmail;

    @Override
    public void send(String email, String code) {
        this.lastEmail = email;
        this.lastCode = code;
    }

    public String lastCode() {
        if (lastCode == null) {
            throw new IllegalStateException("Aucun code emis : la demande n'a pas abouti.");
        }
        return lastCode;
    }

    /** @return le dernier code, ou {@code null} si aucun n'a ete emis */
    public String lastCodeOrNull() {
        return lastCode;
    }

    public String lastEmail() {
        return lastEmail;
    }

    public void clear() {
        lastCode = null;
        lastEmail = null;
    }
}
