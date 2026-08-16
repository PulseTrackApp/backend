package com.pulsetrack.backend.billing.dto;

import java.util.List;

import com.pulsetrack.backend.billing.BillingProperties;

/**
 * Une offre du catalogue, prete a etre affichee.
 *
 * @param priceAmount  montant dans l'unite courante de la devise. Le franc CFA
 *                     n'a pas de centime, d'ou un entier
 * @param priceLabel   prix deja mis en forme, « 2 000 FCFA / mois ». Compose cote
 *                     serveur pour qu'Android et iOS affichent le meme texte
 * @param availability {@code COMING_SOON} tant que le paiement n'existe pas : le
 *                     bouton de souscription doit alors etre inactif et porter
 *                     la mention « a venir »
 */
public record PlanResponse(
        String code,
        String name,
        String description,
        long priceAmount,
        String currency,
        String priceLabel,
        BillingProperties.BillingPeriod period,
        BillingProperties.Availability availability,
        boolean highlighted,
        List<String> features) {
}
