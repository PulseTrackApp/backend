package com.pulsetrack.backend.common.error;

/**
 * Un service tiers indispensable a l'operation a echoue ou n'a pas repondu.
 * Traduit en HTTP 502.
 *
 * <p>Distinct d'une 500 : le defaut n'est pas dans notre code, et le client peut
 * raisonnablement reessayer plus tard.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
