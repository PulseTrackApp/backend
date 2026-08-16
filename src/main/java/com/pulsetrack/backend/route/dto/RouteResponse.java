package com.pulsetrack.backend.route.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

/**
 * Un parcours enregistre.
 *
 * <p><strong>{@code points} est nul dans la liste paginee</strong> et rempli sur
 * le detail. Renvoyer trois cents points par ligne de liste couterait un
 * demi-megaoctet pour dessiner vingt vignettes que personne ne regarde de pres.
 *
 * @param distanceMeters      distance de la seance d'origine, estimee par le
 *                            filtre de Kalman. Ne la recalcule pas depuis les
 *                            points : ils sont bruts et decimes, les resommer
 *                            ramenerait la surestimation que le filtre corrige
 * @param loop                vrai quand l'arrivee est a moins de cent metres du
 *                            depart : un circuit, pas un aller simple
 * @param sourceWorkoutId     seance d'origine ; {@code null} si elle a ete
 *                            supprimee depuis — le parcours, lui, survit
 * @param attemptCount        nombre de seances rattachees a ce parcours
 * @param bestDurationSeconds meilleur temps en mouvement ; {@code null} sans
 *                            aucune tentative
 * @param lastAttemptAt       date de la derniere tentative ; {@code null} si
 *                            aucune. Le detail de chaque passage est servi par
 *                            {@code GET /me/routes/{id}/attempts}
 */
public record RouteResponse(
        UUID id,
        String name,
        SportType sportType,
        double distanceMeters,
        double elevationGainMeters,
        boolean loop,
        int pointCount,
        UUID sourceWorkoutId,
        Instant createdAt,
        int attemptCount,
        Long bestDurationSeconds,
        Instant lastAttemptAt,
        List<RoutePointResponse> points) {

    /**
     * @param cumulativeDistanceMeters distance depuis le depart, pour afficher
     *                                 « tu es au km 3,2 » sans rien sommer cote
     *                                 client. Le dernier point vaut exactement
     *                                 {@code distanceMeters}
     */
    public record RoutePointResponse(
            int position,
            double latitude,
            double longitude,
            Double altitude,
            double cumulativeDistanceMeters) {
    }
}
