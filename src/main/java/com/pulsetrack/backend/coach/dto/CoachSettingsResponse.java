package com.pulsetrack.backend.coach.dto;

import com.pulsetrack.backend.coach.CoachingTone;

/**
 * Reglages tels que vus par le client.
 *
 * <p>Il n'y a volontairement <strong>aucun champ de cle API</strong>, pas meme
 * tronquee. Le client sait qu'une cle est enregistree, il n'a jamais besoin de
 * la relire : ce qu'une API ne renvoie pas ne peut pas fuiter.
 *
 * @param apiKeyStored       une cle propre a cet utilisateur est enregistree
 * @param serverKeyAvailable le serveur dispose de sa propre cle ; l'interface
 *                           peut alors masquer le champ de saisie, il n'y a rien
 *                           a fournir
 * @param usable             l'assistant est actif et une cle est disponible,
 *                           quelle qu'en soit la source ; c'est ce champ que
 *                           l'interface teste avant de proposer un conseil
 */
public record CoachSettingsResponse(
        boolean enabled,
        boolean apiKeyStored,
        boolean serverKeyAvailable,
        boolean usable,
        CoachingTone coachingTone,
        boolean weeklyReviewEnabled,
        boolean effortWarningsEnabled) {
}
