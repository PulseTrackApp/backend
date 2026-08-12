package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Codes encore ouverts d'un compte, pour les invalider quand un nouveau est
     * emis : plusieurs codes valides a la fois ne rendent service a personne.
     */
    List<EmailVerificationToken> findByUserIdAndUsedAtIsNull(UUID userId);

    /** Menage des codes perimes, a chaque nouvelle demande. */
    @Modifying
    @Query("delete from EmailVerificationToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
