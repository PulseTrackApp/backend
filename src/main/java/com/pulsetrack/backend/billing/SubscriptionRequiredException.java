package com.pulsetrack.backend.billing;

import com.pulsetrack.backend.billing.dto.PlanResponse;

/**
 * Le compte n'a plus de droit d'usage : c'est le refus qui declenche l'ecran de
 * paiement.
 *
 * <p>Porte l'offre a mettre en avant, pour que l'ecran affiche un prix sans avoir
 * a lancer une seconde requete au moment ou, justement, toutes les requetes sont
 * refusees.
 */
public class SubscriptionRequiredException extends RuntimeException {

    private final SubscriptionStatus status;
    private final transient PlanResponse suggestedPlan;

    public SubscriptionRequiredException(SubscriptionStatus status, PlanResponse suggestedPlan) {
        super("Ton acces est arrive a echeance. Choisis une formule pour continuer.");
        this.status = status;
        this.suggestedPlan = suggestedPlan;
    }

    public SubscriptionStatus status() {
        return status;
    }

    /** {@code null} si le catalogue est vide — configuration incomplete. */
    public PlanResponse suggestedPlan() {
        return suggestedPlan;
    }
}
