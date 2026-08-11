package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Demandes encore ouvertes d'un compte, pour les invalider quand une
     * nouvelle arrive : laisser plusieurs codes valides en meme temps multiplie
     * les chances d'un attaquant sans rendre service a l'utilisateur.
     */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);

    /**
     * Menage des codes perimes, appele a chaque nouvelle demande. Un code expire
     * n'est plus opposable a personne.
     */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
