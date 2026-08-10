package com.pulsetrack.backend.coach;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * La cle primaire etant deja {@code userId}, les methodes heritees suffisent :
 * {@code findById(userId)} est deja filtre sur le proprietaire.
 */
public interface GeminiSettingsRepository extends JpaRepository<GeminiSettings, UUID> {
}
