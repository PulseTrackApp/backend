package com.pulsetrack.backend.admin.dto;

import java.time.Instant;
import java.util.List;

import com.pulsetrack.backend.billing.BillingPlan;
import com.pulsetrack.backend.billing.BillingCatalog;
import com.pulsetrack.backend.billing.BillingProperties;

/**
 * Une offre vue par l'administration.
 *
 * <p>Ajoute a ce que voit l'application deux choses dont elle n'a que faire mais
 * qui manquent pour editer : l'ordre d'affichage et le nombre de comptes qui ont
 * souscrit. Ce dernier est ce qui permet de comprendre, avant de cliquer, pourquoi
 * une suppression sera refusee.
 *
 * @param priceLabel   prix mis en forme exactement comme l'application l'affichera,
 *                     pour verifier le rendu sans lancer le telephone
 * @param subscriberCount nombre de comptes citant ce code
 */
public record AdminPlanResponse(
        String code,
        String name,
        String description,
        long priceAmount,
        String currency,
        String priceLabel,
        BillingProperties.BillingPeriod period,
        BillingProperties.Availability availability,
        boolean highlighted,
        List<String> features,
        int displayOrder,
        long subscriberCount,
        Instant updatedAt) {

    public static AdminPlanResponse of(BillingPlan plan, long subscriberCount) {
        return new AdminPlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                BillingCatalog.priceLabel(plan.getPriceAmount(), plan.getCurrency(), plan.getPeriod()),
                plan.getPeriod(),
                plan.getAvailability(),
                plan.isHighlighted(),
                plan.getFeatures(),
                plan.getDisplayOrder(),
                subscriberCount,
                plan.getUpdatedAt());
    }
}
