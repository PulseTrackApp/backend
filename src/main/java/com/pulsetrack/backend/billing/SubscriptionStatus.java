package com.pulsetrack.backend.billing;

/**
 * Droit d'usage d'un compte.
 *
 * <p>Constante de protocole : le client route son ecran la-dessus.
 *
 * <p>Volontairement pauvre — quatre valeurs, pas de nuance sur le moyen de
 * paiement ni sur la relance. Ce qui compte du point de vue de l'API tient en une
 * question : ce compte a-t-il le droit d'utiliser l'application aujourd'hui.
 */
public enum SubscriptionStatus {

    /** Periode d'essai en cours. L'application fonctionne normalement. */
    TRIAL,

    /** Abonnement payant en cours de validite. */
    ACTIVE,

    /** L'essai ou l'abonnement est termine. C'est le cas qui declenche le paiement. */
    EXPIRED,

    /**
     * Aucun droit et aucun essai. Reserve a un compte suspendu a la main : un
     * compte ordinaire passe par {@link #TRIAL} puis {@link #EXPIRED}.
     */
    NONE;

    /** Vrai quand le compte peut utiliser l'application. */
    public boolean grantsAccess() {
        return this == TRIAL || this == ACTIVE;
    }
}
