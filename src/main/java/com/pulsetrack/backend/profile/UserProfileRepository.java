package com.pulsetrack.backend.profile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /**
     * Charge le profil et ses sports preferes en une requete : sans l'entity
     * graph, la collection lazy declencherait une seconde requete a chaque lecture.
     */
    @EntityGraph(attributePaths = "preferredSports")
    Optional<UserProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
