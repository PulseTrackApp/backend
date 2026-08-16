package com.pulsetrack.backend.route.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Enregistrement d'un parcours a partir d'une seance deja courue.
 *
 * <p>On ne cree pas un parcours ex nihilo : un circuit qu'on veut reprendre est
 * forcement un circuit qu'on a fait. Le trace, la distance, le denivele et le
 * sport viennent donc tous de la seance, il n'y a rien a saisir.
 *
 * @param name nom sous lequel le retrouver ; unique pour un meme compte, la
 *             casse etant ignoree
 */
public record CreateRouteRequest(
        @NotNull UUID workoutId,
        @NotBlank @Size(max = 120) String name) {
}
