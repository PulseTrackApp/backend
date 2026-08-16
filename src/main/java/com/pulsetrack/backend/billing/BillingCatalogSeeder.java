package com.pulsetrack.backend.billing;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recopie en base, une seule fois, le catalogue declare en configuration.
 *
 * <p><strong>Uniquement si la table est vide.</strong> C'est toute la subtilite :
 * sans cette condition, chaque redemarrage ecraserait les prix corriges depuis
 * l'application d'administration par ceux du fichier de configuration — un bug
 * silencieux, qui ne se verrait qu'au prochain deploiement.
 *
 * <p>La configuration garde donc un role : donner un catalogue de depart a une
 * base neuve, pour qu'un premier lancement n'affiche pas un ecran de tarifs vide.
 * Passe ce moment, elle n'est plus relue.
 */
@Component
public class BillingCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BillingCatalogSeeder.class);

    private final BillingPlanRepository plans;
    private final BillingProperties properties;

    public BillingCatalogSeeder(BillingPlanRepository plans, BillingProperties properties) {
        this.plans = plans;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (plans.count() > 0) {
            return;
        }
        List<BillingProperties.Plan> configured = properties.plans();
        if (configured == null || configured.isEmpty()) {
            log.info("Catalogue de tarifs vide : aucune offre en base ni en configuration.");
            return;
        }

        Instant now = Instant.now();
        int order = 0;
        boolean highlightTaken = false;
        for (BillingProperties.Plan source : configured) {
            // Une seule offre mise en avant : la base le garantit par un index
            // unique partiel, et une configuration qui en marquerait deux ferait
            // echouer le demarrage. On garde la premiere.
            boolean highlighted = source.highlighted() && !highlightTaken;
            highlightTaken = highlightTaken || highlighted;

            BillingPlan plan = new BillingPlan(source.code(), now);
            plan.apply(source.name(),
                    source.description(),
                    source.priceAmount(),
                    source.currency(),
                    source.period(),
                    source.availability(),
                    highlighted,
                    source.features(),
                    order,
                    now);
            plans.save(plan);
            order += 10;
        }
        log.info("Catalogue de tarifs amorcé depuis la configuration : {} offre(s).", configured.size());
    }
}
