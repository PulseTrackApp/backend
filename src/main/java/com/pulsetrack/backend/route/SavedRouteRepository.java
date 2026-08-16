package com.pulsetrack.backend.route;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acces aux parcours enregistres.
 *
 * <p>Comme partout ailleurs, toutes les methodes portent {@code userId} dans leur
 * critere : c'est ce qui rend impossible, par construction, de lire ou de
 * supprimer le circuit de quelqu'un d'autre en devinant son identifiant.
 */
public interface SavedRouteRepository extends JpaRepository<SavedRoute, UUID> {

    /** Detail d'un parcours : le trace est charge dans la meme requete. */
    @EntityGraph(attributePaths = "points")
    Optional<SavedRoute> findByIdAndUserId(UUID id, UUID userId);

    /** Existence sans charger le trace, pour valider un rattachement de seance. */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * Le seul nom, sans le trace.
     *
     * <p>Distincte de {@link #findByIdAndUserId} a dessein : celle-ci charge les
     * points par {@code @EntityGraph}. L'appeler pour afficher un nom dans une
     * liste de vingt defis chargerait six mille points de geometrie pour rien.
     */
    @Query("select r.name from SavedRoute r where r.id = :id and r.userId = :userId")
    Optional<String> findNameByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Liste paginee, sans le trace. */
    Page<SavedRoute> findByUserId(UUID userId, Pageable pageable);

    Page<SavedRoute> findByUserIdAndSportType(UUID userId, SportType sportType, Pageable pageable);

    /**
     * Deux circuits du meme nom chez la meme personne rendraient l'ecran de choix
     * indechiffrable. La casse est ignoree, comme le fait l'index unique en base :
     * cette verification rend un message clair, l'index sert de garde-fou.
     */
    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Tous les parcours d'un compte, pour l'export des donnees personnelles. */
    List<SavedRoute> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
