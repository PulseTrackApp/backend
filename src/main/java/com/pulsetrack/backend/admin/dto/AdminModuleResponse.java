package com.pulsetrack.backend.admin.dto;

import com.pulsetrack.backend.access.AppModule;

/**
 * Entree du catalogue des modules.
 *
 * @param label    libelle lisible, pour que l'ecran d'administration n'ait pas a
 *                 coder en dur une traduction des identifiants — ajouter un
 *                 module ne demandera alors aucune livraison cote client
 * @param lockable toujours {@code true} aujourd'hui. Le champ existe pour le
 *                 jour ou un module deviendra obligatoire : le client saura
 *                 griser la case au lieu de laisser croire a un reglage
 *                 possible, et le contrat n'aura pas a changer
 */
public record AdminModuleResponse(AppModule module, String label, boolean lockable) {
}
