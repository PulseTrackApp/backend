package com.pulsetrack.backend.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration Firebase Cloud Messaging.
 *
 * <p>Aucune contrainte de validation ici : desactive, le module doit accepter
 * des valeurs vides. La verification a lieu au moment de construire le client,
 * uniquement quand {@code enabled} vaut vrai.
 *
 * <p>Le compte de service peut arriver par deux chemins, selon ce que la
 * plateforme d'hebergement sait faire : un fichier monte dans le conteneur, ou
 * son contenu encode en base64 dans une variable d'environnement. Coolify ne
 * sachant pas alimenter un fichier monte depuis un compose, c'est la seconde
 * forme qui sert en production.
 *
 * @param enabled             active l'envoi reel ; sinon les notifications sont
 *                            seulement journalisees
 * @param projectId           identifiant du projet Firebase
 * @param credentialsLocation emplacement du JSON de compte de service, au format
 *                            Spring ({@code file:...} ou {@code classpath:...})
 * @param credentialsBase64   contenu du meme JSON encode en base64 ; prioritaire
 *                            sur {@code credentialsLocation} quand les deux sont
 *                            renseignes
 */
@ConfigurationProperties(prefix = "pulsetrack.push.fcm")
public record PushProperties(boolean enabled,
                             String projectId,
                             String credentialsLocation,
                             String credentialsBase64) {
}
