package com.pulsetrack.backend.billing;

import java.util.List;

import com.pulsetrack.backend.billing.dto.PlanResponse;
import com.pulsetrack.backend.billing.dto.SubscriptionResponse;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarifs et droit d'usage du compte courant.
 *
 * <p>Les deux routes restent ouvertes meme quand le paiement est exige : un
 * compte expire doit pouvoir consulter les tarifs et comprendre pourquoi il est
 * bloque. Un ecran de paiement dont les prix se font refuser n'aurait aucun sens.
 */
@RestController
public class BillingController {

    private final SubscriptionService subscriptions;

    public BillingController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * Catalogue des offres.
     *
     * <p>Aujourd'hui toutes marquees {@code COMING_SOON} : l'ecran s'affiche, le
     * bouton de souscription reste inactif. C'est la demande — preparer la
     * tarification, pas la mettre en vente.
     */
    @GetMapping("/api/v1/billing/plans")
    public List<PlanResponse> plans() {
        return subscriptions.plans();
    }

    /**
     * Droit d'usage du compte courant.
     *
     * <p>A appeler au demarrage et apres chaque retour au premier plan, comme
     * {@code /me/modules}. Le champ {@code accessGranted} est le seul sur lequel
     * router l'ecran.
     */
    @GetMapping("/api/v1/me/subscription")
    public SubscriptionResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return subscriptions.describe(AuthenticatedUser.idOf(jwt));
    }
}
