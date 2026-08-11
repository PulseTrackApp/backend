package com.pulsetrack.backend.admin.dto;

import java.util.Set;

import com.pulsetrack.backend.access.AppModule;

import jakarta.validation.constraints.NotNull;

/**
 * Nouvel ensemble complet des modules d'un compte.
 *
 * <p>Un ensemble vide est valide et signifie « tout fermer » ; c'est
 * {@code null} qui est refuse, parce qu'il ne veut rien dire et trahirait un
 * client qui a oublie le champ plutot qu'une intention.
 *
 * <p>Un identifiant inconnu fait echouer la deserialisation en {@code 400} : le
 * client est renvoye a sa faute au lieu de voir son reglage silencieusement
 * ampute d'un module mal orthographie.
 */
public record UpdateModulesRequest(@NotNull Set<AppModule> modules) {
}
