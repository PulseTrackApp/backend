package com.pulsetrack.backend.admin;

import java.util.List;

import com.pulsetrack.backend.admin.dto.AdminBillingSettingsResponse;
import com.pulsetrack.backend.admin.dto.AdminPlanResponse;
import com.pulsetrack.backend.admin.dto.PlanUpsertRequest;
import com.pulsetrack.backend.billing.BillingCatalog;
import com.pulsetrack.backend.billing.BillingPlan;
import com.pulsetrack.backend.billing.SubscriptionService;
import com.pulsetrack.backend.client.ClientProperties;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion du catalogue de tarifs.
 *
 * <p>Aucune annotation de securite ici : {@code /api/v1/admin/**} est ferme dans
 * la chaine de filtres, si bien qu'un endpoint ajoute demain dans cette classe
 * nait protege.
 *
 * <p>Ce que cet ecran <strong>ne</strong> fait pas : mettre l'application en
 * vente. Les prix se corrigent ici, l'interrupteur reste chez l'hebergeur — voir
 * {@link AdminBillingSettingsResponse}.
 */
@RestController
@RequestMapping("/api/v1/admin/billing")
public class AdminBillingController {

    private static final Logger log = LoggerFactory.getLogger(AdminBillingController.class);

    private final BillingCatalog catalog;
    private final SubscriptionService subscriptions;
    private final ClientProperties clientProperties;

    public AdminBillingController(BillingCatalog catalog,
                                  SubscriptionService subscriptions,
                                  ClientProperties clientProperties) {
        this.catalog = catalog;
        this.subscriptions = subscriptions;
        this.clientProperties = clientProperties;
    }

    @GetMapping("/plans")
    public List<AdminPlanResponse> plans() {
        return catalog.stored().stream().map(this::describe).toList();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPlanResponse create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody PlanUpsertRequest request) {
        BillingPlan created = catalog.create(
                request.code(),
                request.name(),
                request.description(),
                request.priceAmount(),
                request.currency(),
                request.period(),
                request.availability(),
                request.highlighted(),
                request.features(),
                request.displayOrder());
        log.info("Administrateur {} a créé l'offre {}", AuthenticatedUser.idOf(jwt), created.getCode());
        return describe(created);
    }

    /**
     * Remplace une offre. Le code vient du chemin, jamais du corps : il est
     * reference par les abonnements deja poses.
     */
    @PutMapping("/plans/{code}")
    public AdminPlanResponse update(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable String code,
                                    @Valid @RequestBody PlanUpsertRequest request) {
        BillingPlan updated = catalog.update(
                code,
                request.name(),
                request.description(),
                request.priceAmount(),
                request.currency(),
                request.period(),
                request.availability(),
                request.highlighted(),
                request.features(),
                request.displayOrder());
        log.info("Administrateur {} a modifié l'offre {}", AuthenticatedUser.idOf(jwt), code);
        return describe(updated);
    }

    @DeleteMapping("/plans/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        catalog.delete(code);
        log.info("Administrateur {} a supprimé l'offre {}", AuthenticatedUser.idOf(jwt), code);
    }

    /** Etat des deux verrous et duree d'essai. Lecture seule, a dessein. */
    @GetMapping("/settings")
    public AdminBillingSettingsResponse settings() {
        return new AdminBillingSettingsResponse(
                subscriptions.isEnforced(),
                subscriptions.trialDays(),
                clientProperties.enforced(),
                clientProperties.minimumVersion());
    }

    private AdminPlanResponse describe(BillingPlan plan) {
        return AdminPlanResponse.of(plan, catalog.subscriberCount(plan.getCode()));
    }
}
