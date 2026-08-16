package com.pulsetrack.backend.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.billing.dto.PlanResponse;
import com.pulsetrack.backend.billing.dto.SubscriptionResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.user.User;
import com.pulsetrack.backend.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Droits d'usage et catalogue de tarifs.
 *
 * <p><strong>L'essai ne se stocke pas, il se calcule.</strong> Un compte sans
 * ligne d'abonnement est en essai jusqu'a {@code creation + trialDays}. Ce choix
 * evite deux choses : creer une ligne a chaque inscription, et surtout devoir en
 * fabriquer retroactivement pour tous les comptes existants le jour ou la
 * facturation arrive. Il a une consequence assumee : les comptes deja anciens
 * seront immediatement expires quand le verrou s'activera. C'est le comportement
 * voulu, ils ont eu leur essai.
 *
 * <p>Le fuseau retenu est celui du serveur. Une echeance a la journee ne merite
 * pas un fuseau par utilisateur, et l'ecart possible est d'un jour au pire, en
 * faveur de l'utilisateur.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final BillingProperties properties;

    public SubscriptionService(SubscriptionRepository subscriptions,
                               UserRepository users,
                               BillingProperties properties) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.properties = properties;
    }

    /** Vrai si le compte a le droit d'utiliser l'application aujourd'hui. */
    @Transactional(readOnly = true)
    public boolean hasAccess(UUID userId) {
        return statusOf(userId).status().grantsAccess();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse describe(UUID userId) {
        Entitlement entitlement = statusOf(userId);
        LocalDate endsOn = entitlement.endsOn();
        Integer daysLeft = endsOn == null
                ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(today(), endsOn));

        return new SubscriptionResponse(
                entitlement.status(),
                entitlement.status().grantsAccess(),
                properties.enforced(),
                endsOn,
                daysLeft,
                entitlement.planCode(),
                headlineOf(entitlement.status()),
                messageOf(entitlement.status(), daysLeft));
    }

    /**
     * Etat effectif du compte, ligne enregistree ou essai calcule.
     *
     * @throws ResourceNotFoundException si le compte n'existe pas ; sans sa date
     *                                   de creation, l'essai n'est pas calculable
     */
    @Transactional(readOnly = true)
    public Entitlement statusOf(UUID userId) {
        Optional<Subscription> stored = subscriptions.findByUserId(userId);
        if (stored.isPresent()) {
            Subscription subscription = stored.get();
            return new Entitlement(
                    subscription.effectiveStatusOn(today()),
                    subscription.getCurrentPeriodEnd(),
                    subscription.getPlanCode());
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));
        LocalDate trialEnd = trialEndOf(user);
        SubscriptionStatus status = trialEnd.isBefore(today())
                ? SubscriptionStatus.EXPIRED
                : SubscriptionStatus.TRIAL;
        return new Entitlement(status, trialEnd, null);
    }

    /**
     * Pose ou remplace le droit d'un compte. Reserve a l'administration tant
     * qu'aucun encaissement automatique n'existe.
     *
     * @throws BusinessRuleException si l'offre citee n'est pas au catalogue :
     *                               enregistrer un code inconnu produirait un
     *                               abonnement que plus aucun ecran ne sait
     *                               afficher
     */
    @Transactional
    public SubscriptionResponse grant(UUID userId,
                                      SubscriptionStatus status,
                                      String planCode,
                                      LocalDate periodEnd,
                                      String note) {
        if (!users.existsById(userId)) {
            throw new ResourceNotFoundException("Compte introuvable.");
        }
        if (planCode != null && planOf(planCode).isEmpty()) {
            throw new BusinessRuleException("Offre inconnue : " + planCode);
        }

        Instant now = Instant.now();
        Subscription subscription = subscriptions.findByUserId(userId)
                .orElseGet(() -> new Subscription(userId, now));
        subscription.apply(status, planCode, periodEnd, note, now);
        subscriptions.save(subscription);

        return describe(userId);
    }

    /** Catalogue affiche par l'ecran de tarifs. */
    public List<PlanResponse> plans() {
        List<BillingProperties.Plan> configured = properties.plans();
        if (configured == null) {
            return List.of();
        }
        return configured.stream().map(this::toResponse).toList();
    }

    /**
     * Offre a mettre en avant dans le refus de paiement. La premiere marquee
     * comme telle, ou la premiere du catalogue a defaut : un ecran de paiement
     * sans aucun prix n'aide personne.
     */
    public Optional<PlanResponse> highlightedPlan() {
        List<PlanResponse> all = plans();
        return all.stream().filter(PlanResponse::highlighted).findFirst()
                .or(() -> all.stream().findFirst());
    }

    public boolean isEnforced() {
        return properties.enforced();
    }

    private Optional<BillingProperties.Plan> planOf(String code) {
        List<BillingProperties.Plan> configured = properties.plans();
        if (configured == null) {
            return Optional.empty();
        }
        return configured.stream().filter(plan -> plan.code().equals(code)).findFirst();
    }

    /**
     * Dernier jour <strong>inclus</strong> de l'essai.
     *
     * <p>Une duree negative ne pose pas d'essai du tout : l'echeance tombe avant
     * la creation du compte. C'est ce qui permet d'eprouver le refus de paiement
     * sans attendre trente jours, et ce qui permettra un jour de supprimer
     * l'essai sans toucher au code.
     */
    private LocalDate trialEndOf(User user) {
        return user.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(properties.trialDays());
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private PlanResponse toResponse(BillingProperties.Plan plan) {
        return new PlanResponse(
                plan.code(),
                plan.name(),
                plan.description(),
                plan.priceAmount(),
                plan.currency(),
                priceLabelOf(plan),
                plan.period(),
                plan.availability(),
                plan.highlighted(),
                plan.features() == null ? List.of() : List.copyOf(plan.features()));
    }

    /**
     * Prix mis en forme cote serveur, pour qu'Android et iOS affichent le meme
     * texte et que la devise ne se retrouve pas codee en dur dans deux clients.
     */
    private String priceLabelOf(BillingProperties.Plan plan) {
        String amount = Wording.decimal(plan.priceAmount(), 0);
        String suffix = switch (plan.period()) {
            case MONTHLY -> " / mois";
            case YEARLY -> " / an";
            case LIFETIME -> " une fois";
        };
        return amount + " " + plan.currency() + suffix;
    }

    private String headlineOf(SubscriptionStatus status) {
        return switch (status) {
            case TRIAL -> "Periode d'essai";
            case ACTIVE -> "Abonnement actif";
            case EXPIRED -> "Abonnement a renouveler";
            case NONE -> "Aucun acces";
        };
    }

    private String messageOf(SubscriptionStatus status, Integer daysLeft) {
        return switch (status) {
            case TRIAL -> daysLeft == null
                    ? "Tu es en periode d'essai."
                    : "Il te reste %s d'essai.".formatted(
                            Wording.plural(daysLeft, "jour", "jours"));
            case ACTIVE -> daysLeft == null
                    ? "Ton abonnement est actif."
                    : "Ton abonnement court encore %s.".formatted(
                            Wording.plural(daysLeft, "jour", "jours"));
            case EXPIRED -> "Ton acces est arrive a echeance. Choisis une formule pour continuer.";
            case NONE -> "Ce compte n'a pas d'acces actif.";
        };
    }

    /**
     * Etat effectif d'un compte.
     *
     * @param endsOn dernier jour inclus ; {@code null} pour un droit sans
     *               echeance
     */
    public record Entitlement(SubscriptionStatus status, LocalDate endsOn, String planCode) {
    }
}
