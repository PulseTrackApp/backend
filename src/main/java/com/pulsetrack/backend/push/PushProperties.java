package com.pulsetrack.backend.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration Firebase Cloud Messaging.
 *
 * <p>Aucune contrainte de validation ici : desactive, le module doit accepter
 * des valeurs vides. La verification a lieu au moment de construire le client,
 * uniquement quand {@code enabled} vaut vrai.
 *
 * @param enabled             active l'envoi reel ; sinon les notifications sont
 *                            seulement journalisees
 * @param projectId           identifiant du projet Firebase
 * @param credentialsLocation emplacement du JSON de compte de service, au format
 *                            Spring ({@code file:...} ou {@code classpath:...})
 */
@ConfigurationProperties(prefix = "pulsetrack.push.fcm")
public record PushProperties(boolean enabled, String projectId, String credentialsLocation) {
}
