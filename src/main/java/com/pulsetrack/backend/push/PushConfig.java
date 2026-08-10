package com.pulsetrack.backend.push;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestClient;

/**
 * Choisit l'implementation d'envoi selon la configuration.
 *
 * <p>Les deux beans portent une condition sur la <em>meme</em> propriete, avec
 * des valeurs opposees : exactement un des deux est cree, toujours. C'est plus
 * sur qu'un {@code @ConditionalOnMissingBean}, dont l'ordre d'evaluation n'est
 * garanti que dans l'auto-configuration.
 */
@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /** Portee minimale : le droit d'envoyer des messages, rien d'autre. */
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.push.fcm", name = "enabled", havingValue = "true")
    PushSender fcmPushSender(RestClient.Builder builder,
                             PushProperties properties,
                             ResourceLoader resourceLoader) {
        if (properties.projectId() == null || properties.projectId().isBlank()) {
            throw new IllegalStateException(
                    "pulsetrack.push.fcm.enabled=true exige pulsetrack.push.fcm.project-id");
        }
        if (properties.credentialsLocation() == null || properties.credentialsLocation().isBlank()) {
            throw new IllegalStateException(
                    "pulsetrack.push.fcm.enabled=true exige pulsetrack.push.fcm.credentials-location");
        }

        GoogleCredentials credentials = loadCredentials(properties.credentialsLocation(), resourceLoader);
        log.info("Notifications push actives via FCM, projet {}", properties.projectId());
        return new FcmPushSender(builder, credentials, properties.projectId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "pulsetrack.push.fcm", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    PushSender loggingPushSender() {
        log.info("Notifications push en mode journalisation : FCM n'est pas configure.");
        return new LoggingPushSender();
    }

    /**
     * Echoue au demarrage si le compte de service est absent ou illisible :
     * decouvrir le probleme dimanche 19h, au moment ou le rappel devait partir,
     * serait bien pire.
     */
    private GoogleCredentials loadCredentials(String location, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream stream = resource.getInputStream()) {
            return GoogleCredentials.fromStream(stream).createScoped(List.of(FCM_SCOPE));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Compte de service Firebase illisible a l'emplacement " + location, ex);
        }
    }
}
