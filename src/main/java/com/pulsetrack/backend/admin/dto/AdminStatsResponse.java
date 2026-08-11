package com.pulsetrack.backend.admin.dto;

import java.util.List;

import com.pulsetrack.backend.access.AppModule;

/**
 * Agregats du tableau de bord.
 *
 * <p>Tous calcules par des {@code count} en base. Charger les entites pour les
 * compter en memoire ferait tomber un serveur a deux processeurs bien avant que
 * le produit ait des utilisateurs.
 *
 * @param activeUsers7Days   comptes ayant enregistre au moins une seance depuis
 *                           sept jours. « Actif » se mesure a l'usage reel, pas
 *                           a une connexion : ouvrir l'application sans rien y
 *                           faire ne dit pas qu'on s'en sert
 * @param workoutsThisMonth  seances depuis le premier du mois. Le calcul est en
 *                           UTC, ce qui est exact ici : le fuseau du produit,
 *                           Africa/Ouagadougou, est a GMT+0 toute l'annee et
 *                           sans heure d'ete
 * @param moduleUsage        tous les modules connus, y compris ceux accordes a
 *                           personne — sinon le tableau aurait des trous que le
 *                           client devrait combler lui-meme
 */
public record AdminStatsResponse(long totalUsers,
                                 long admins,
                                 long activeUsers7Days,
                                 long activeUsers30Days,
                                 long workoutsThisMonth,
                                 List<ModuleUsage> moduleUsage) {

    public record ModuleUsage(AppModule module, long users) {
    }
}
