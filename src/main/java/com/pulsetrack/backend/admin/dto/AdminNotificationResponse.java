package com.pulsetrack.backend.admin.dto;

/**
 * Ce qu'est devenue la notification une fois postee.
 *
 * <p>Les deux compteurs disent des choses differentes et il faut les deux :
 * {@code recipients} est le nombre de comptes vises, {@code devices} le nombre
 * d'appareils reellement joints. Un ecart entre les deux se lit tout seul — des
 * comptes sans appareil enregistre, ou des jetons perimes ecartes au passage.
 * Un envoi qui n'atteint personne doit se voir a l'ecran, pas se deviner.
 *
 * @param recipients comptes vises par l'envoi
 * @param devices    appareils ayant accepte la notification
 */
public record AdminNotificationResponse(int recipients, int devices) {
}
