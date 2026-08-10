package com.pulsetrack.backend.push;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat d'enregistrement des appareils destinataires des notifications.
 */
class DeviceTokenApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private DeviceTokenRepository deviceTokens;

    @Test
    void enregistre_un_appareil_et_reste_idempotent() throws Exception {
        String token = registerUser();
        String fcmToken = "fcm-token-" + java.util.UUID.randomUUID();

        // L'application appelle cet endpoint a chaque demarrage : deux appels
        // identiques ne doivent produire qu'une ligne.
        register(token, fcmToken).andExpect(status().isNoContent());
        register(token, fcmToken).andExpect(status().isNoContent());

        assertThat(deviceTokens.findByToken(fcmToken)).isPresent();
        assertThat(deviceTokens.findAll().stream()
                .filter(device -> device.getToken().equals(fcmToken))
                .count()).isEqualTo(1);
    }

    @Test
    void reattribue_un_appareil_qui_change_de_compte() throws Exception {
        String fcmToken = "fcm-token-" + java.util.UUID.randomUUID();

        String alice = registerUser();
        register(alice, fcmToken).andExpect(status().isNoContent());
        var afterAlice = deviceTokens.findByToken(fcmToken).orElseThrow();

        String bob = registerUser();
        register(bob, fcmToken).andExpect(status().isNoContent());
        var afterBob = deviceTokens.findByToken(fcmToken).orElseThrow();

        // Meme telephone reconnecte avec un autre compte : il change de
        // proprietaire, sinon Alice recevrait les rappels de Bob.
        assertThat(afterBob.getUserId()).isNotEqualTo(afterAlice.getUserId());
        assertThat(deviceTokens.findByUserId(afterAlice.getUserId())).isEmpty();
    }

    @Test
    void supprime_un_appareil_a_la_deconnexion() throws Exception {
        String token = registerUser();
        String fcmToken = "fcm-token-" + java.util.UUID.randomUUID();
        register(token, fcmToken).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/me/device-tokens/" + fcmToken).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(deviceTokens.findByToken(fcmToken)).isEmpty();
    }

    @Test
    void ne_supprime_pas_l_appareil_d_un_autre_compte() throws Exception {
        String fcmToken = "fcm-token-" + java.util.UUID.randomUUID();
        String alice = registerUser();
        register(alice, fcmToken).andExpect(status().isNoContent());

        String bob = registerUser();
        mockMvc.perform(delete("/api/v1/me/device-tokens/" + fcmToken).header("Authorization", bob))
                .andExpect(status().isNotFound());

        assertThat(deviceTokens.findByToken(fcmToken)).isPresent();
    }

    @Test
    void refuse_une_plateforme_inconnue() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/device-tokens")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "abc123456789", "platform": "BLACKBERRY"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refuse_un_enregistrement_sans_jeton() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/device-tokens")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "", "platform": "ANDROID"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions register(String authorization,
                                                                        String fcmToken) throws Exception {
        return mockMvc.perform(put("/api/v1/me/device-tokens")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "platform": "ANDROID"}
                        """.formatted(fcmToken)));
    }
}
