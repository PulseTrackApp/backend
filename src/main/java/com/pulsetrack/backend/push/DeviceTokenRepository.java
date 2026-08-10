package com.pulsetrack.backend.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    /**
     * Recherche par jeton seul, sans {@code userId} : c'est justement ainsi qu'on
     * detecte qu'un appareil a change de proprietaire.
     */
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    Optional<DeviceToken> findByTokenAndUserId(String token, UUID userId);

    /**
     * Utilisateurs ayant au moins un appareil enregistre.
     *
     * <p>Les traitements planifies partent de cette liste plutot que de tous les
     * comptes : notifier quelqu'un qui n'a aucun appareil n'a aucun effet, autant
     * ne pas calculer son bilan pour rien.
     */
    @Query("select distinct d.userId from DeviceToken d")
    List<UUID> findDistinctUserIds();
}
