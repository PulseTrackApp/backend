package com.pulsetrack.backend.user;

/**
 * Expediteur de test : retient le code au lieu de l'envoyer.
 *
 * <p>C'est le seul moyen d'eprouver le parcours de bout en bout — le code
 * n'apparait nulle part dans les reponses HTTP, precisement pour qu'on ne
 * puisse pas le recuperer sans acceder a la boite aux lettres.
 */
public class RecordingResetCodeSender implements ResetCodeSender {

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

    public String lastEmail() {
        return lastEmail;
    }

    public void clear() {
        lastCode = null;
        lastEmail = null;
    }
}
