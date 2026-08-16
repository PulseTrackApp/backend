package com.pulsetrack.backend.challenge;

/**
 * Ou en est un defi.
 *
 * <pre>
 * DRAFT ──start──▶ ACTIVE ──complete──▶ SUCCEEDED | FAILED
 *   │                 │
 *   │                 └──abandon──▶ ABANDONED
 *   └──(expiresOn depassee)──▶ EXPIRED
 * </pre>
 *
 * <p>{@link #EXPIRED} est pose par le serveur et jamais par le client. Un defi
 * <strong>arme</strong> n'expire pas par la date : quelqu'un qui court a minuit
 * ne doit pas voir son defi s'evaporer sous ses pieds.
 *
 * <p>Constante de protocole : ces identifiants circulent tels quels dans l'API.
 */
public enum ChallengeStatus {

    /** Cree, pas encore arme. Le chronometre n'a pas commence. */
    DRAFT,

    /** En cours. L'echeance est posee, il n'y en a qu'un a la fois par compte. */
    ACTIVE,

    SUCCEEDED,

    FAILED,

    /** Interrompu par l'utilisateur. Ni reussite ni echec : un renoncement. */
    ABANDONED,

    /** Jamais tente avant sa date limite. */
    EXPIRED;

    /** Vrai tant que le sort du defi n'est pas joue. */
    public boolean isOpen() {
        return this == DRAFT || this == ACTIVE;
    }

    /** Vrai une fois le defi termine, d'une facon ou d'une autre. */
    public boolean isSettled() {
        return !isOpen();
    }
}
