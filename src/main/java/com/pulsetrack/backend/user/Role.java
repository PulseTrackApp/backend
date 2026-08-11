package com.pulsetrack.backend.user;

/**
 * Niveau de privilege d'un compte.
 *
 * <p>Le role s'ajoute, il ne remplace pas : un administrateur reste un
 * utilisateur ordinaire, avec ses seances, ses pesees et l'application mobile
 * qui fonctionne normalement. Il gagne l'acces a l'espace d'administration, il
 * ne perd rien.
 */
public enum Role {

    USER,

    /**
     * Acces a {@code /api/v1/admin/**}, et immunite au verrouillage par module :
     * un administrateur prive d'une fonctionnalite ne pourrait plus la
     * reouvrir a personne, y compris a lui-meme.
     */
    ADMIN
}
