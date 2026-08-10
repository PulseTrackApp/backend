package com.pulsetrack.backend.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /** Objectifs en cours, ceux dont on calcule la progression. */
    List<Goal> findByUserIdAndActiveTrue(UUID userId);

    List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Sert a detecter le doublon avant l'insertion, pour repondre 409 avec un
     * message clair plutot que de laisser remonter la violation d'index.
     */
    Optional<Goal> findByUserIdAndTypeAndActiveTrue(UUID userId, GoalType type);
}
