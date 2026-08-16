package com.pulsetrack.backend.admin.dto;

import java.time.LocalDate;

import com.pulsetrack.backend.billing.SubscriptionStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Droit d'usage pose a la main sur un compte.
 *
 * <p>C'est le seul moyen d'accorder un acces tant qu'aucun encaissement
 * automatique n'existe : compte offert, abonnement encaisse hors ligne, compte
 * suspendu. C'est aussi ce qui permet de mettre un compte de test en
 * {@code EXPIRED} pour eprouver l'ecran de paiement sans attendre la fin d'un
 * essai.
 *
 * @param planCode  offre souscrite ; doit exister au catalogue. {@code null}
 *                  pour une simple suspension
 * @param periodEnd dernier jour <strong>inclus</strong> de validite ;
 *                  {@code null} pour un droit sans echeance
 * @param note      pourquoi ce droit est pose. Un acces accorde sans explication
 *                  devient inexplicable six mois plus tard
 */
public record UpdateSubscriptionRequest(
        @NotNull SubscriptionStatus status,
        @Size(max = 40) String planCode,
        LocalDate periodEnd,
        @Size(max = 500) String note) {
}
