package com.pulsetrack.backend.push;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
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
        if (isBlank(properties.projectId())) {
            throw new IllegalStateException(
                    "pulsetrack.push.fcm.enabled=true exige pulsetrack.push.fcm.project-id");
        }
        if (isBlank(properties.credentialsBase64()) && isBlank(properties.credentialsLocation())) {
            throw new IllegalStateException("pulsetrack.push.fcm.enabled=true exige un compte de service, "
                    + "par pulsetrack.push.fcm.credentials-base64 ou credentials-location");
        }

        GoogleCredentials credentials = loadCredentials(properties, resourceLoader);
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
     *
     * <p>Le base64 l'emporte sur le fichier : c'est la forme utilisee en
     * production, alors qu'un emplacement de fichier peut trainer dans la
     * configuration par defaut.
     */
    private GoogleCredentials loadCredentials(PushProperties properties, ResourceLoader resourceLoader) {
        if (!isBlank(properties.credentialsBase64())) {
            return fromBase64(properties.credentialsBase64());
        }
        return fromLocation(properties.credentialsLocation(), resourceLoader);
    }

    private GoogleCredentials fromLocation(String location, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream stream = resource.getInputStream()) {
            return scoped(stream);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Compte de service Firebase illisible a l'emplacement " + location, ex);
        }
    }

    /**
     * Compte de service passe en variable d'environnement, faute de pouvoir
     * monter un fichier : Coolify ne sait pas alimenter le contenu d'un montage
     * declare dans un compose.
     *
     * <p>Aucun message d'erreur ne reprend la valeur recue, meme tronquee : ce
     * sont les octets d'une cle privee, et un journal se recopie facilement.
     */
    static GoogleCredentials fromBase64(String encoded) {
        byte[] decoded;
        try {
            // Les espaces et retours a la ligne sont retires avant decodage :
            // la plupart des commandes `base64` coupent la sortie toutes les 64
            // ou 76 colonnes, et refuser ce format ferait echouer le demarrage
            // sur un detail de mise en forme.
            //
            // Le decodeur strict, et non `getMimeDecoder()` : ce dernier ignore
            // en silence tout caractere invalide, si bien qu'une valeur
            // franchement erronee se decoderait en octets arbitraires et
            // echouerait plus loin, sur un message trompeur.
            decoded = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "pulsetrack.push.fcm.credentials-base64 n'est pas du base64 valide", ex);
        }

        try (InputStream stream = new ByteArrayInputStream(decoded)) {
            return scoped(stream);
        } catch (IOException ex) {
            throw new IllegalStateException("pulsetrack.push.fcm.credentials-base64 ne contient pas "
                    + "un compte de service Firebase valide", ex);
        }
    }

    private static GoogleCredentials scoped(InputStream stream) throws IOException {
        return GoogleCredentials.fromStream(stream).createScoped(List.of(FCM_SCOPE));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
