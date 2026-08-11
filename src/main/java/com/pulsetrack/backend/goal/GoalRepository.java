package com.pulsetrack.backend.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Objectifs en cours, ceux dont on calcule la progression.
     *
     * <p>Non pagine a dessein : un seul objectif actif par type, et il n'y a
     * qu'une poignee de types. Le calcul de progression a besoin de tous.
     */
    List<Goal> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Objectifs actifs, page par page, pour l'affichage.
     *
     * <p>Meme ensemble que ci-dessus, mais l'ecran de consultation passe par la
     * meme forme paginee que l'historique : un client qui sait lire une page
     * n'a pas a distinguer les deux cas.
     */
    Page<Goal> findByUserIdAndActiveTrue(UUID userId, Pageable pageable);

    /**
     * Historique complet, archives comprises.
     *
     * <p>Pagine parce que celui-ci grossit sans limite : un objectif
     * hebdomadaire par semaine et par type, ce sont des dizaines de lignes par
     * an que personne n'affichera jamais d'un coup.
     */
    Page<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Sert a detecter le doublon avant l'insertion, pour repondre 409 avec un
     * message clair plutot que de laisser remonter la violation d'index.
     */
    Optional<Goal> findByUserIdAndTypeAndActiveTrue(UUID userId, GoalType type);
}
