package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Recherche par empreinte seule : le client presente son jeton sans dire qui
     * il est, c'est le jeton qui designe le compte.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Jetons encore utilisables d'un compte. Sert a couper toutes les sessions
     * d'un coup quand un jeton deja consomme est rejoue.
     */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Menage des jetons perimes, appele a chaque emission.
     *
     * <p>Un jeton expire n'est plus opposable a personne : le garder ne ferait
     * que faire grossir la table a chaque connexion. Les jetons revoques mais
     * non encore expires sont conserves — ce sont eux qui permettent de
     * reconnaitre un rejeu.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt < :now")
    int deleteExpiredFor(@Param("userId") UUID userId, @Param("now") Instant now);
}
