package com.pulsetrack.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ce que l'application envoie depuis l'ecran d'aide.
 *
 * <p>Aucun destinataire, aucune adresse d'expedition : le serveur seul sait ou
 * cela part, et l'emetteur est celui que le jeton designe. Accepter ces champs
 * ferait du formulaire un relais de courriel ouvert.
 *
 * <p>Les longueurs sont bornees parce que le message finit dans un courriel :
 * sans bornes, un corps de requete de plusieurs megaoctets partirait tel quel.
 */
public record SupportMessageRequest(
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 4000) String message) {
}
