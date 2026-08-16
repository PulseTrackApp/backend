package com.pulsetrack.backend.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.pulsetrack.backend.billing.dto.PlanResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.motivation.Wording;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le catalogue de tarifs : lecture par l'application, edition par l'administration.
 *
 * <p><strong>La base fait foi.</strong> La configuration
 * {@code pulsetrack.billing.plans} ne sert plus qu'a amorcer une base vide
 * ({@link BillingCatalogSeeder}) et de filet si le catalogue enregistre venait a
 * l'etre : un ecran de tarifs sans aucune offre n'aide personne, alors que des
 * prix d'attente restent lisibles.
 *
 * <p>Le prix affiche est compose ici, pas dans les clients. C'est ce qui garantit
 * qu'Android, iOS et l'administration montrent exactement le meme texte, et que
 * la devise n'est codee en dur nulle part.
 */
@Service
public class BillingCatalog {

    private final BillingPlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final BillingProperties properties;

    public BillingCatalog(BillingPlanRepository plans,
                          SubscriptionRepository subscriptions,
                          BillingProperties properties) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.properties = properties;
    }

    /** Catalogue enregistre, ou celui de la configuration si la base est vide. */
    @Transactional(readOnly = true)
    public List<PlanResponse> catalogue() {
        List<BillingPlan> stored = plans.findAllByOrderByDisplayOrderAscCodeAsc();
        if (!stored.isEmpty()) {
            return stored.stream().map(BillingCatalog::toResponse).toList();
        }
        List<BillingProperties.Plan> configured = properties.plans();
        if (configured == null) {
            return List.of();
        }
        return configured.stream().map(BillingCatalog::toResponse).toList();
    }

    /** Les offres enregistrees, dans l'ordre d'affichage. Pour l'administration. */
    @Transactional(readOnly = true)
    public List<BillingPlan> stored() {
        return plans.findAllByOrderByDisplayOrderAscCodeAsc();
    }

    /** Combien de comptes citent cette offre. Voir {@link #delete(String)}. */
    @Transactional(readOnly = true)
    public long subscriberCount(String code) {
        return subscriptions.countByPlanCode(code);
    }

    @Transactional(readOnly = true)
    public Optional<PlanResponse> find(String code) {
        return catalogue().stream().filter(plan -> plan.code().equals(code)).findFirst();
    }

    /**
     * Offre a proposer dans un refus de paiement : celle mise en avant, ou la
     * premiere du catalogue a defaut. Un ecran de paiement sans aucun prix
     * n'aide personne.
     */
    @Transactional(readOnly = true)
    public Optional<PlanResponse> highlighted() {
        List<PlanResponse> all = catalogue();
        return all.stream().filter(PlanResponse::highlighted).findFirst()
                .or(() -> all.stream().findFirst());
    }

    /**
     * Cree une offre.
     *
     * @throws BusinessRuleException si le code est deja pris. Ecraser
     *                               silencieusement une offre existante ferait
     *                               d'une faute de frappe une perte de donnees
     */
    @Transactional
    public BillingPlan create(String code,
                              String name,
                              String description,
                              long priceAmount,
                              String currency,
                              BillingProperties.BillingPeriod period,
                              BillingProperties.Availability availability,
                              boolean highlight,
                              List<String> features,
                              int displayOrder) {
        String normalized = normalizeCode(code);
        if (plans.existsById(normalized)) {
            throw new BusinessRuleException("Une offre porte deja le code " + normalized + ".");
        }

        Instant now = Instant.now();
        if (highlight) {
            releaseHighlight(normalized, now);
        }
        BillingPlan plan = new BillingPlan(normalized, now);
        plan.apply(name, description, priceAmount, currency, period, availability,
                highlight, features, displayOrder, now);
        return plans.save(plan);
    }

    /**
     * Remplace une offre. Le code n'est pas modifiable : il est reference par les
     * abonnements deja poses, et le changer les orphelinerait sans bruit.
     */
    @Transactional
    public BillingPlan update(String code,
                              String name,
                              String description,
                              long priceAmount,
                              String currency,
                              BillingProperties.BillingPeriod period,
                              BillingProperties.Availability availability,
                              boolean highlight,
                              List<String> features,
                              int displayOrder) {
        BillingPlan plan = plans.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + code));

        Instant now = Instant.now();
        if (highlight) {
            releaseHighlight(code, now);
        }
        plan.apply(name, description, priceAmount, currency, period, availability,
                highlight, features, displayOrder, now);
        return plans.save(plan);
    }

    /**
     * Retire une offre du catalogue.
     *
     * @throws BusinessRuleException si des comptes la citent encore. Le bon geste
     *                               est alors de la passer en {@code RETIRED} :
     *                               elle disparait de la vente, et les abonnes en
     *                               cours la conservent
     */
    @Transactional
    public void delete(String code) {
        BillingPlan plan = plans.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + code));

        long referencing = subscriptions.countByPlanCode(code);
        if (referencing > 0) {
            throw new BusinessRuleException(
                    "%s est encore souscrite par %s. Passe l'offre en RETIRED plutôt que de la supprimer."
                            .formatted(code, Wording.plural((int) referencing, "compte", "comptes")));
        }
        plans.delete(plan);
    }

    /**
     * Libere la mise en avant detenue par une autre offre.
     *
     * <p>Fait avant l'ecriture, dans la meme transaction : l'index unique partiel
     * de la base refuserait deux offres mises en avant, et l'ordre inverse
     * echouerait a chaque changement de recommandation.
     */
    private void releaseHighlight(String except, Instant now) {
        for (BillingPlan other : plans.findAllByHighlightedTrue()) {
            if (!other.getCode().equals(except)) {
                other.clearHighlight(now);
                plans.save(other);
            }
        }
        plans.flush();
    }

    /**
     * Le code circule dans l'API et sert de cle : majuscules et sans espace, pour
     * que {@code monthly} et {@code MONTHLY} ne deviennent pas deux offres.
     */
    private static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase();
        if (normalized.isEmpty()) {
            throw new BusinessRuleException("Le code de l'offre est obligatoire.");
        }
        return normalized;
    }

    static PlanResponse toResponse(BillingPlan plan) {
        return new PlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                priceLabel(plan.getPriceAmount(), plan.getCurrency(), plan.getPeriod()),
                plan.getPeriod(),
                plan.getAvailability(),
                plan.isHighlighted(),
                plan.getFeatures());
    }

    static PlanResponse toResponse(BillingProperties.Plan plan) {
        return new PlanResponse(
                plan.code(),
                plan.name(),
                plan.description(),
                plan.priceAmount(),
                plan.currency(),
                priceLabel(plan.priceAmount(), plan.currency(), plan.period()),
                plan.period(),
                plan.availability(),
                plan.highlighted(),
                plan.features() == null ? List.of() : List.copyOf(plan.features()));
    }

    /** Prix mis en forme cote serveur : un seul texte, les memes sur tous les clients. */
    public static String priceLabel(long amount, String currency, BillingProperties.BillingPeriod period) {
        String suffix = switch (period) {
            case MONTHLY -> " / mois";
            case YEARLY -> " / an";
            case LIFETIME -> " une fois";
        };
        return Wording.decimal(amount, 0) + " " + currency + suffix;
    }
}
