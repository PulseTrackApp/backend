package com.pulsetrack.backend.bodycheckin;

import java.time.LocalDate;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux releves physiques. Comme partout, chaque methode filtre sur
 * {@code userId} : l'isolation entre comptes est portee par la requete.
 */
public interface BodyCheckInRepository extends JpaRepository<BodyCheckIn, UUID> {

    Optional<BodyCheckIn> findByIdAndUserId(UUID id, UUID userId);

    Optional<BodyCheckIn> findByUserIdAndCheckinDate(UUID userId, LocalDate checkinDate);

    Page<BodyCheckIn> findByUserId(UUID userId, Pageable pageable);

    /**
     * Serie chronologique complete, pour tracer les courbes de progression.
     *
     * <p>Non paginee a dessein : un releve hebdomadaire produit environ 52 lignes
     * par an, et une courbe amputee serait trompeuse. Si la frequence devenait
     * quotidienne, il faudrait borner sur une fenetre de dates.
     */
    List<BodyCheckIn> findByUserIdOrderByCheckinDateAsc(UUID userId);

    /** Dernier releve connu : c'est lui qui fait foi pour le poids courant. */
    Optional<BodyCheckIn> findFirstByUserIdOrderByCheckinDateDesc(UUID userId);

    /** Tout premier releve : point de depart d'un objectif de poids. */
    Optional<BodyCheckIn> findFirstByUserIdOrderByCheckinDateAsc(UUID userId);

    /**
     * Releves d'une periode, bornes incluses.
     *
     * <p>Bornes inclusives ici, contrairement aux seances : un releve porte une
     * date et non un instant, et le dernier jour de la periode en fait partie.
     */
    List<BodyCheckIn> findByUserIdAndCheckinDateBetweenOrderByCheckinDateAsc(
            UUID userId, LocalDate from, LocalDate to);
}
