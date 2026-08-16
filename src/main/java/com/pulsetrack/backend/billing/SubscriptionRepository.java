package com.pulsetrack.backend.billing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux droits d'usage enregistres.
 *
 * <p>L'absence de ligne n'est pas une anomalie : c'est le cas normal d'un compte
 * en periode d'essai. Voir {@link Subscription}.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    /**
     * Combien de comptes citent cette offre.
     *
     * <p>Interroge avant de supprimer une offre du catalogue : effacer un code
     * encore reference laisserait des abonnements pointant vers rien, et plus
     * aucun ecran ne saurait dire ce que ces comptes ont souscrit.
     */
    long countByPlanCode(String planCode);
}
