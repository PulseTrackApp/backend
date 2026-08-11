package com.pulsetrack.backend.access.dto;

import java.util.List;

/**
 * Droits du compte courant.
 *
 * <p>Enveloppe autour de la liste plutot que la liste nue : un tableau au
 * premier niveau d'une reponse JSON interdit d'y ajouter le moindre champ plus
 * tard sans casser tous les clients deja livres.
 *
 * @param modules tous les modules connus du serveur, actives ou non, dans un
 *                ordre stable
 */
public record ModulesResponse(List<ModuleStateResponse> modules) {
}
