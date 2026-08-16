package com.pulsetrack.backend.billing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, String> {

    /**
     * Catalogue dans l'ordre d'affichage.
     *
     * <p>Le code departage les egalites : sans ce second critere, deux offres de
     * meme rang changeraient de place d'un appel a l'autre, et l'ecran de tarifs
     * semblerait bouger tout seul.
     */
    List<BillingPlan> findAllByOrderByDisplayOrderAscCodeAsc();

    List<BillingPlan> findAllByHighlightedTrue();
}
