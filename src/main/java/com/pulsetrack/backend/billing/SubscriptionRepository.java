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
}
