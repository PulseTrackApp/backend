package com.pulsetrack.backend.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Suspendre ou rouvrir un compte.
 *
 * <p>Un seul endpoint pour les deux sens, avec l'etat vise dans le corps :
 * l'ecran envoie ce qu'il veut obtenir, et rejouer l'appel ne peut pas produire
 * un etat different de celui qui est affiche. Deux routes {@code /ban} et
 * {@code /unban} inviteraient au contraire a raisonner en bascule, et une
 * double soumission rouvrirait ce qu'on venait de fermer.
 *
 * @param disabled etat vise : vrai pour suspendre, faux pour rouvrir
 * @param reason   pourquoi. Exige a la suspension par le service — une
 *                 suspension muette devient inexplicable six mois plus tard —
 *                 et ignore a la reouverture, ou elle ne concerne plus rien
 */
public record UpdateAccountStatusRequest(boolean disabled,
                                         @Size(max = 500) String reason) {
}
