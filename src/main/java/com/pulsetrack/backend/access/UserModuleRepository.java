package com.pulsetrack.backend.access;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserModuleRepository extends JpaRepository<UserModule, UUID> {

    /**
     * Projection sur la seule colonne utile : c'est la requete faite a chaque
     * appel authentifie dont la route est protegee, et charger les entites
     * completes pour n'en lire qu'un champ couterait un aller-retour de plus
     * sur un serveur qui n'a que deux processeurs.
     */
    @Query("select m.module from UserModule m where m.userId = :userId")
    Set<AppModule> findModulesByUserId(@Param("userId") UUID userId);

    List<UserModule> findByUserId(UUID userId);

    /**
     * Droits de plusieurs comptes en une requete, pour la liste paginee de
     * l'administration. Interroger compte par compte ferait vingt requetes pour
     * une page de vingt lignes — le defaut classique du « N+1 ».
     */
    List<UserModule> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Repartition d'usage pour le tableau de bord. Un module accorde a personne
     * n'apparait pas dans le resultat : c'est a l'appelant de completer avec les
     * modules absents, sans quoi le tableau aurait des trous.
     */
    @Query("""
            select new com.pulsetrack.backend.access.ModuleUsageRow(m.module, count(m))
            from UserModule m
            group by m.module
            """)
    List<ModuleUsageRow> countUsersByModule();

    /**
     * Efface les droits d'un compte avant de reecrire l'ensemble. Le remplacement
     * complet evite qu'un ecran d'administration et la base divergent, ce qui
     * arriverait avec des ajouts et retraits unitaires si un appel se perdait.
     */
    @Modifying
    @Query("delete from UserModule m where m.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
