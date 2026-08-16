package com.pulsetrack.backend.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nouveau nom d'un parcours. Seul le nom se modifie : la geometrie, elle, est le
 * releve d'une sortie reelle et n'a pas a etre retouchee.
 */
public record RenameRouteRequest(@NotBlank @Size(max = 120) String name) {
}
