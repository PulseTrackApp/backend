package com.pulsetrack.backend.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.user.Role;

/**
 * Compte tel que l'administration le voit.
 *
 * <p>Ne contient aucune donnee sportive : ni poids, ni seance, ni echange avec
 * le coach. Administrer des acces ne demande pas de lire l'intimite des gens, et
 * ce qui n'est pas expose ici ne peut pas fuiter par cet ecran.
 *
 * @param enabledModules modules accordes, dans l'ordre de declaration, pour que
 *                       l'ecran affiche toujours ses cases dans le meme ordre
 */
public record AdminUserResponse(UUID id,
                                String email,
                                Role role,
                                Instant createdAt,
                                List<AppModule> enabledModules) {
}
