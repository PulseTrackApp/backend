package com.pulsetrack.backend.common.error;

/**
 * Requete syntaxiquement valide mais refusee par une regle metier
 * (ex. une seance qui se termine avant d'avoir commence). Traduit en HTTP 422.
 *
 * <p>Distinct de la Bean Validation, qui couvre la forme des champs pris
 * isolement et repond 400.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
