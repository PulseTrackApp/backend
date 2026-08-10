package com.pulsetrack.backend.workout.dto;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Un point du trace envoye par le mobile.
 *
 * <p>Latitude et longitude sont des objets et non des primitives : un champ
 * absent du JSON vaudrait {@code 0.0} sur une primitive, une coordonnee valide
 * au large de l'Afrique, et passerait donc la validation sans bruit.
 *
 * @param altitude altitude en metres, si le capteur la fournit
 * @param accuracy precision horizontale en metres, telle qu'annoncee par le GPS
 * @param speed    vitesse instantanee en m/s mesuree par le capteur
 */
public record GpsPointRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Double altitude,
        Double accuracy,
        Double speed,
        @NotNull Instant recordedAt) {
}
