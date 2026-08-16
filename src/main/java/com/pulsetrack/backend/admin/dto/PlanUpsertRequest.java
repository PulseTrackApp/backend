package com.pulsetrack.backend.admin.dto;

import java.util.List;

import com.pulsetrack.backend.billing.BillingProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Etat complet d'une offre, tel que l'ecran d'administration l'envoie.
 *
 * <p>Remplacement complet et non modification partielle : l'ecran envoie ce qu'il
 * affiche, et rejouer l'appel ne peut pas produire un etat different de celui que
 * l'administrateur a sous les yeux.
 *
 * <p>{@code code} n'est lu qu'a la creation. Sur une modification il est ignore
 * au profit du chemin : le code est reference par les abonnements deja poses, et
 * le changer les orphelinerait sans bruit.
 *
 * @param priceAmount montant dans l'unite courante de la devise. Zero est
 *                    accepte — une offre gratuite est une decision, pas une faute
 */
public record PlanUpsertRequest(
        @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @PositiveOrZero long priceAmount,
        @NotBlank @Size(max = 10) String currency,
        @NotNull BillingProperties.BillingPeriod period,
        @NotNull BillingProperties.Availability availability,
        boolean highlighted,
        List<@Size(max = 200) String> features,
        int displayOrder) {
}
