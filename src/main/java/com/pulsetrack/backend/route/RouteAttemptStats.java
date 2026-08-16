package com.pulsetrack.backend.route;

import java.time.Instant;
import java.util.UUID;

/**
 * Resume des tentatives sur un parcours, agrege par la base.
 *
 * <p>Agreger en base plutot que charger les seances : la liste des parcours en
 * affiche vingt par page, et une requete par parcours ferait vingt et un
 * allers-retours la ou un seul suffit.
 *
 * @param bestMovingDurationSeconds meilleur temps <strong>en mouvement</strong>,
 *                                  et non duree totale : s'arreter lacer sa
 *                                  chaussure ne veut pas dire courir moins vite
 */
public record RouteAttemptStats(
        UUID routeId,
        long attemptCount,
        Long bestMovingDurationSeconds,
        Instant lastAttemptAt) {
}
