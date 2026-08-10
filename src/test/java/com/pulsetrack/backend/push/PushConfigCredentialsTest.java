package com.pulsetrack.backend.push;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du chargement du compte de service Firebase, sans Spring.
 *
 * <p>Le compte de service est fabrique ici, avec une paire de cles generee a la
 * volee : rien n'est fige dans le depot, et la cle est reellement analysee par
 * la bibliotheque Google — un test sur un JSON factice ne prouverait rien,
 * puisque c'est justement le decodage de la cle privee qui echoue en vrai.
 */
class PushConfigCredentialsTest {

    private static final String PROJECT_ID = "gymflow-test";
    private static final String CLIENT_EMAIL =
            "firebase-adminsdk-test@gymflow-test.iam.gserviceaccount.com";

    @Test
    void charge_un_compte_de_service_encode_en_base64() {
        String encoded = base64(serviceAccountJson());

        GoogleCredentials credentials = PushConfig.fromBase64(encoded);

        assertThat(credentials).isInstanceOf(ServiceAccountCredentials.class);
        ServiceAccountCredentials serviceAccount = (ServiceAccountCredentials) credentials;
        assertThat(serviceAccount.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(serviceAccount.getClientEmail()).isEqualTo(CLIENT_EMAIL);
    }

    @Test
    void restreint_les_droits_a_l_envoi_de_messages() {
        GoogleCredentials credentials = PushConfig.fromBase64(base64(serviceAccountJson()));

        // Portee minimale : le compte peut envoyer des notifications, rien
        // d'autre de l'ecosysteme Firebase.
        assertThat(credentials.createScopedRequired()).isFalse();
        assertThat(((ServiceAccountCredentials) credentials).getScopes())
                .containsExactly("https://www.googleapis.com/auth/firebase.messaging");
    }

    @Test
    void tolere_les_retours_a_la_ligne_du_base64() {
        // `base64` sans -w0 coupe toutes les 76 colonnes : exiger une ligne
        // unique ferait echouer le demarrage sur un detail de mise en forme.
        String wrapped = Base64.getMimeEncoder()
                .encodeToString(serviceAccountJson().getBytes(StandardCharsets.UTF_8));
        assertThat(wrapped).contains("\n");

        assertThat(PushConfig.fromBase64(wrapped)).isNotNull();
    }

    @Test
    void refuse_une_valeur_qui_n_est_pas_du_base64() {
        // Le decodeur strict rejette les caracteres invalides. Un decodeur MIME
        // les ignorerait en silence : la valeur se decoderait en octets
        // arbitraires, et l'exploitant recevrait « ce n'est pas un compte de
        // service » alors que son probleme est une valeur tronquee au
        // copier-coller.
        assertThatThrownBy(() -> PushConfig.fromBase64("ceci n'est pas du base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("n'est pas du base64 valide");
    }

    @Test
    void refuse_un_base64_tronque() {
        String complete = base64(serviceAccountJson());
        String truncated = complete.substring(0, complete.length() - 3);

        // Cas le plus probable en exploitation : un copier-coller incomplet.
        assertThatThrownBy(() -> PushConfig.fromBase64(truncated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuse_un_base64_valide_qui_n_est_pas_un_compte_de_service() {
        String encoded = base64("{\"hello\": \"world\"}");

        assertThatThrownBy(() -> PushConfig.fromBase64(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compte de service Firebase valide");
    }

    @Test
    void ne_recopie_jamais_le_secret_dans_le_message_d_erreur() {
        String encoded = base64("{\"type\":\"service_account\",\"private_key\":\"FUITE-A-NE-PAS-JOURNALISER\"}");

        assertThatThrownBy(() -> PushConfig.fromBase64(encoded))
                .isInstanceOf(IllegalStateException.class)
                // Un message d'erreur se recopie dans un ticket, un chat, une
                // alerte : la valeur recue ne doit y figurer sous aucune forme.
                .hasMessageNotContaining("FUITE-A-NE-PAS-JOURNALISER")
                .hasMessageNotContaining(encoded);
    }

    private static String base64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compte de service minimal mais authentique : la cle privee est une vraie
     * cle RSA, au format PKCS#8 attendu par la bibliotheque Google.
     */
    private static String serviceAccountJson() {
        String privateKeyPem = generatePrivateKeyPem();
        return """
                {
                  "type": "service_account",
                  "project_id": "%s",
                  "private_key_id": "0123456789abcdef",
                  "private_key": "%s",
                  "client_email": "%s",
                  "client_id": "123456789012345678901",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.formatted(PROJECT_ID, privateKeyPem.replace("\n", "\\n"), CLIENT_EMAIL);
    }

    private static String generatePrivateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                    .encodeToString(keyPair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA indisponible sur cette JVM", ex);
        }
    }
}
