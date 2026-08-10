package com.pulsetrack.backend.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrait l'identifiant de l'utilisateur courant du jeton valide.
 *
 * <p>Le {@code subject} du JWT porte l'identifiant du compte. Passer par ce
 * point unique evite que chaque controller reinvente la conversion, et rend
 * evident le fait que l'identite ne vient <em>jamais</em> du corps de la requete.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static UUID idOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
