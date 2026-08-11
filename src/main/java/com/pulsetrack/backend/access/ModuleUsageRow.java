package com.pulsetrack.backend.access;

/**
 * Nombre de comptes auxquels un module est accorde.
 *
 * <p>Calcule par un {@code group by} en base plutot qu'en parcourant les droits
 * de chaque compte : le tableau de bord d'administration ne doit pas couter un
 * chargement complet de la table sur un serveur a deux processeurs.
 *
 * @param users nombre de comptes, en {@code long} car c'est ce que rend
 *              {@code count()} — un {@code int} obligerait a une conversion
 *              dans la requete elle-meme
 */
public record ModuleUsageRow(AppModule module, long users) {
}
