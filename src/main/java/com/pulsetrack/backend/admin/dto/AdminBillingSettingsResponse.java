package com.pulsetrack.backend.admin.dto;

/**
 * Les interrupteurs de la mise en vente, en lecture seule.
 *
 * <p><strong>Pourquoi ils ne s'editent pas depuis l'ecran.</strong> Ce ne sont
 * pas des tarifs : {@code billingEnforced} ferme l'application a tout compte sans
 * droit, et il n'a de sens qu'apres {@code clientEnforced}, qui met hors service
 * les applications trop anciennes. Dans l'autre ordre, il suffit de ne pas mettre
 * a jour pour continuer gratuitement. Un basculement de cette portee passe par
 * l'hebergeur — un bouton le rendrait trop facile a actionner par curiosite.
 *
 * <p>L'ecran les montre pour repondre a la seule question qui compte en les
 * regardant : « est-ce que c'est deja en vigueur ? »
 *
 * @param billingEnforced l'API refuse-t-elle deja les comptes sans droit
 * @param trialDays       duree d'essai, comptee depuis la creation du compte
 * @param clientEnforced  l'API refuse-t-elle deja les applications trop anciennes
 * @param minimumVersion  version minimale acceptee quand le verrou est actif
 */
public record AdminBillingSettingsResponse(
        boolean billingEnforced,
        int trialDays,
        boolean clientEnforced,
        String minimumVersion) {
}
