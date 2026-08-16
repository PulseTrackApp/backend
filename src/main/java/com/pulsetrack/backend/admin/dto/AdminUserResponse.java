package com.pulsetrack.backend.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.user.Role;
import com.pulsetrack.backend.user.User;

/**
 * Compte tel que l'administration le voit.
 *
 * <p>Ne contient aucune donnee sportive : ni poids, ni seance, ni echange avec
 * le coach. Administrer des acces ne demande pas de lire l'intimite des gens, et
 * ce qui n'est pas expose ici ne peut pas fuiter par cet ecran.
 *
 * @param emailVerified  adresse confirmee ou non. Utile a l'administration : une
 *                       adresse jamais confirmee est le premier suspect quand
 *                       quelqu'un se plaint de ne recevoir aucun courriel
 * @param enabledModules modules accordes, dans l'ordre de declaration, pour que
 *                       l'ecran affiche toujours ses cases dans le meme ordre
 * @param disabled       compte suspendu. Redondant avec {@code disabledAt}, et
 *                       c'est voulu : un client qui teste une date pour decider
 *                       d'un affichage finit par se tromper de sens
 * @param disabledAt     depuis quand ; {@code null} pour un compte actif
 * @param disabledReason pourquoi. Peut etre nul meme sur un compte suspendu, si
 *                       la suspension vient d'ailleurs que de l'ecran
 */
public record AdminUserResponse(UUID id,
                                String email,
                                Role role,
                                boolean emailVerified,
                                Instant createdAt,
                                List<AppModule> enabledModules,
                                boolean disabled,
                                Instant disabledAt,
                                String disabledReason) {

    public static AdminUserResponse of(User user, List<AppModule> enabledModules) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                enabledModules,
                user.isDisabled(),
                user.getDisabledAt(),
                user.getDisabledReason());
    }
}
