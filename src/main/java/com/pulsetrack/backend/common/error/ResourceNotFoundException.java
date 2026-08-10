package com.pulsetrack.backend.common.error;

/**
 * Ressource demandee inexistante, ou appartenant a un autre utilisateur.
 *
 * <p>Les deux cas sont volontairement confondus : repondre 403 sur la ressource
 * d'autrui confirmerait son existence, ce qui permet d'enumerer les identifiants.
 * On repond 404 dans les deux cas.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
