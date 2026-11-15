package com.pulsetrack.backend.support;

import java.util.UUID;

/**
 * Un message ecrit depuis l'application, pret a etre poste.
 *
 * <p>Le contexte technique voyage avec le message, et ce n'est pas un detail :
 * un signalement sans version ni identifiant de compte oblige a un aller-retour
 * avant meme de commencer a chercher. L'ecran d'aide affichait ces informations
 * en invitant a les recopier ; les joindre soi-meme evite l'oubli.
 *
 * @param userId      compte emetteur, tel que le jeton le designe — jamais tel
 *                    que le corps de la requete le pretend
 * @param senderEmail adresse du compte, mise en repondre-a pour qu'une reponse
 *                    parte d'un simple clic
 * @param appVersion  version de l'application, telle qu'elle s'annonce dans ses
 *                    en-tetes ; nulle si l'application ne l'annonce pas
 * @param platform    systeme du telephone, meme remarque
 */
public record SupportMessage(UUID userId,
                             String senderEmail,
                             String appVersion,
                             String platform,
                             String subject,
                             String body) {
}
