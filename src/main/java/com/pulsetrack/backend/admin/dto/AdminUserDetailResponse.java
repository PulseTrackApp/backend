package com.pulsetrack.backend.admin.dto;

import java.time.Instant;

import com.pulsetrack.backend.billing.dto.SubscriptionResponse;

/**
 * Fiche d'un compte : le meme objet que dans la liste, plus de quoi juger de son
 * activite et de son droit d'usage.
 *
 * <p>Composition plutot qu'heritage : les enregistrements Java ne s'etendent
 * pas, et l'imbrication garde une forme JSON previsible pour le client.
 *
 * <p><strong>Des compteurs, jamais du contenu.</strong> Savoir si un compte sert
 * encore, et a quelle intensite, ne demande pas de lire ou l'utilisateur a
 * couru, ce qu'il pese ou ce qu'il a demande au coach. La ligne est tenue ici :
 * cette fiche porte des nombres et des dates, rien qui raconte une seance.
 *
 * @param usage        cumuls d'entrainement
 * @param subscription droit d'usage, tel que l'utilisateur le voit lui-meme
 */
public record AdminUserDetailResponse(AdminUserResponse account,
                                      AdminUsageResponse usage,
                                      SubscriptionResponse subscription) {

    /**
     * Ce que le compte a fait de l'application.
     *
     * @param workoutCount           nombre total de seances
     * @param workoutsLastSevenDays  seances des sept derniers jours : la seule
     *                               mesure qui distingue un compte vivant d'un
     *                               compte qui a beaucoup servi puis s'est arrete
     * @param workoutsLastThirtyDays seances des trente derniers jours
     * @param totalDistanceMeters    distance cumulee, <strong>en metres</strong>
     * @param totalMovingSeconds     temps en mouvement cumule
     * @param firstWorkoutAt         {@code null} si le compte n'a jamais rien
     *                               enregistre
     * @param lastWorkoutAt          idem ; c'est « depuis quand il ne s'entraine
     *                               plus »
     */
    public record AdminUsageResponse(long workoutCount,
                                     long workoutsLastSevenDays,
                                     long workoutsLastThirtyDays,
                                     double totalDistanceMeters,
                                     long totalMovingSeconds,
                                     Instant firstWorkoutAt,
                                     Instant lastWorkoutAt) {
    }
}
