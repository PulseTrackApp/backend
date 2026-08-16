package com.pulsetrack.backend.challenge;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acces aux defis.
 *
 * <p>Toutes les lectures portent {@code userId} : le defi de quelqu'un d'autre
 * ne se lit pas en devinant un identifiant.
 */
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByIdAndUserId(UUID id, UUID userId);

    Page<Challenge> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Challenge> findByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId,
                                                                Collection<ChallengeStatus> statuses,
                                                                Pageable pageable);

    /**
     * Le defi arme, s'il y en a un. L'unicite est garantie en base par un index
     * partiel : deux echeances simultanees ne veulent rien dire.
     */
    Optional<Challenge> findByUserIdAndStatus(UUID userId, ChallengeStatus status);

    /**
     * Defis encore ouverts dont la date limite est passee, a fermer.
     *
     * <p>Seuls les {@code DRAFT} sont concernes : un defi arme ne doit pas
     * s'evaporer sous les pieds de quelqu'un qui court.
     */
    @Query("""
            select c from Challenge c
            where c.status = com.pulsetrack.backend.challenge.ChallengeStatus.DRAFT
              and c.expiresOn is not null
              and c.expiresOn < :today
            """)
    List<Challenge> findExpiredDrafts(@Param("today") LocalDate today);

    /** Defis non tentes dont la date limite approche, pour le rappel du jour. */
    @Query("""
            select c from Challenge c
            where c.status = com.pulsetrack.backend.challenge.ChallengeStatus.DRAFT
              and c.expiresOn is not null
              and c.expiresOn between :today and :until
            """)
    List<Challenge> findDraftsExpiringBetween(@Param("today") LocalDate today,
                                              @Param("until") LocalDate until);

    /** Defis rattaches a une seance supprimee. Le resultat obtenu reste acquis. */
    List<Challenge> findByUserIdAndWorkoutId(UUID userId, UUID workoutId);

    long countByUserIdAndStatus(UUID userId, ChallengeStatus status);

    List<Challenge> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
