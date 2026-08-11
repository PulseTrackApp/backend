package com.pulsetrack.backend.access.dto;

import com.pulsetrack.backend.access.AppModule;

/**
 * Etat d'un module pour le compte courant.
 *
 * @param module  identifiant stable, a comparer tel quel cote client
 * @param enabled {@code false} signifie que l'application doit presenter la
 *                rubrique comme indisponible, pas la faire disparaitre : une
 *                fonctionnalite qui s'evapore sans explication se lit comme une
 *                panne
 */
public record ModuleStateResponse(AppModule module, boolean enabled) {
}
