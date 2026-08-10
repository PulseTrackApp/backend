package com.pulsetrack.backend.coach;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachMessageRepository extends JpaRepository<CoachMessage, UUID> {

    /** Bilan deja produit pour cette semaine, le cas echeant. */
    Optional<CoachMessage> findByUserIdAndKindAndWeekStart(UUID userId,
                                                           CoachMessageKind kind,
                                                           LocalDate weekStart);

    Page<CoachMessage> findByUserId(UUID userId, Pageable pageable);

    /** Dernier conseil, quel qu'il soit : c'est ce qu'affiche le dashboard. */
    Optional<CoachMessage> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Historique complet, pour l'export. */
    java.util.List<CoachMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
