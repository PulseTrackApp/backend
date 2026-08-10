package com.pulsetrack.backend.push;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Envoi reel via l'API FCM HTTP v1.
 *
 * <p>Deux etapes : obtenir un jeton OAuth2 a partir du compte de service, puis
 * poster le message. La bibliotheque Google ne sert qu'a la premiere ; l'appel
 * HTTP reste explicite, ce qui rend visible ce qui part reellement.
 *
 * <p>Cette classe ne leve jamais d'exception vers l'appelant : elle est invoquee
 * depuis des traitements planifies, ou l'echec d'un appareil ne doit pas
 * interrompre l'envoi aux autres.
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    private static final String FCM_BASE_URL = "https://fcm.googleapis.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final GoogleCredentials credentials;
    private final String projectId;

    public FcmPushSender(RestClient.Builder builder, GoogleCredentials credentials, String projectId) {
        this.credentials = credentials;
        this.projectId = projectId;
        this.restClient = builder
                .baseUrl(FCM_BASE_URL)
                .requestFactory(requestFactory())
                .build();
    }

    @Override
    public boolean send(String token, PushNotification notification) {
        String accessToken;
        try {
            accessToken = freshAccessToken();
        } catch (IOException ex) {
            // Compte de service illisible ou revoque : probleme de configuration,
            // pas de jeton d'appareil. On conserve le jeton.
            log.error("Impossible d'obtenir un jeton d'acces FCM", ex);
            return true;
        }

        SendRequest body = new SendRequest(new Message(
                token,
                new Notification(notification.title(), notification.body()),
                notification.data(),
                // Priorite haute : un rappel qui arrive le lendemain n'est plus
                // un rappel. Android peut malgre tout le differer en mode Doze.
                new AndroidConfig("high")));

        try {
            restClient.post()
                    .uri("/v1/projects/{projectId}/messages:send", projectId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new FcmResponseException(response.getStatusCode());
                    })
                    .toBodilessEntity();
            return true;
        } catch (FcmResponseException ex) {
            return handleErrorStatus(token, ex.status);
        } catch (ResourceAccessException ex) {
            log.warn("FCM injoignable pour {} : {}", PushTokens.masked(token), ex.getMessage());
            return true;
        }
    }

    /**
     * @return {@code false} uniquement si le jeton est definitivement invalide
     */
    private boolean handleErrorStatus(String token, HttpStatusCode status) {
        // 404 : jeton inconnu de FCM, typiquement l'application desinstallee.
        // C'est le seul cas ou le supprimer est la bonne reaction.
        if (status.value() == 404) {
            log.info("Jeton FCM perime, il sera supprime : {}", PushTokens.masked(token));
            return false;
        }
        if (status.value() == 401 || status.value() == 403) {
            log.error("FCM a refuse nos identifiants (statut {}). Verifiez le compte de service.",
                    status.value());
            return true;
        }
        log.warn("FCM a repondu {} pour {}", status.value(), PushTokens.masked(token));
        return true;
    }

    /**
     * Les jetons OAuth2 durent une heure ; la bibliotheque les renouvelle et les
     * met en cache, on peut donc appeler cette methode a chaque envoi.
     */
    private String freshAccessToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** Transporte le statut hors du callback {@code onStatus}. */
    private static final class FcmResponseException extends RuntimeException {
        private final transient HttpStatusCode status;

        private FcmResponseException(HttpStatusCode status) {
            super("FCM a repondu " + status.value());
            this.status = status;
        }
    }

    // --- Structures de l'API FCM HTTP v1 -------------------------------------

    record SendRequest(Message message) {
    }

    record Message(String token, Notification notification, Map<String, String> data,
                   AndroidConfig android) {
    }

    record Notification(String title, String body) {
    }

    record AndroidConfig(String priority) {
    }
}
