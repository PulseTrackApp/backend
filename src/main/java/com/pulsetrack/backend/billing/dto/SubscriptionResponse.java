package com.pulsetrack.backend.billing.dto;

import java.time.LocalDate;

import com.pulsetrack.backend.billing.SubscriptionStatus;

/**
 * Droit d'usage du compte courant, et ce que le client doit en faire.
 *
 * @param status       etat effectif du jour
 * @param accessGranted vrai si l'application fonctionne. <strong>C'est le seul
 *                     champ sur lequel router l'ecran</strong> : le statut sert a
 *                     nuancer le message, pas a decider
 * @param enforced     vrai quand le serveur refuse deja les comptes sans droit.
 *                     Tant qu'il vaut faux, un compte {@code EXPIRED} continue de
 *                     fonctionner — l'ecran de tarifs se prepare, il ne bloque pas
 * @param endsOn       dernier jour inclus de validite ; {@code null} pour un
 *                     droit sans echeance
 * @param daysLeft     jours restants, {@code null} sans echeance et 0 une fois
 *                     l'echeance passee
 * @param planCode     offre souscrite ; {@code null} pendant l'essai
 * @param headline     titre court, redige cote serveur
 * @param message      ce qu'il faut dire a l'utilisateur, pret a afficher
 */
public record SubscriptionResponse(
        SubscriptionStatus status,
        boolean accessGranted,
        boolean enforced,
        LocalDate endsOn,
        Integer daysLeft,
        String planCode,
        String headline,
        String message) {
}
