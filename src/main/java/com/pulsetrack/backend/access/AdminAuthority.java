package com.pulsetrack.backend.access;

import com.pulsetrack.backend.user.Role;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Reconnaissance de l'administrateur a partir des autorites du jeton.
 *
 * <p>Definie une seule fois : l'intercepteur et l'endpoint des droits doivent
 * repondre la meme chose, faute de quoi l'application afficherait une rubrique
 * que le serveur refuse — ou l'inverse, plus vicieux encore.
 */
public final class AdminAuthority {

    private static final String ADMIN = "ROLE_" + Role.ADMIN.name();

    private AdminAuthority() {
    }

    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN::equals);
    }
}
