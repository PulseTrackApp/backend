package com.pulsetrack.backend.rating;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La note de l'utilisateur, vue du client.
 *
 * <p>Le cas le plus important est celui du compte neuf : il ne recoit pas zero
 * mais un accueil. C'est ce que lit quelqu'un qui vient d'installer
 * l'application.
 */
class RatingApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void accueille_un_compte_neuf_au_lieu_de_le_noter_zero() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/rating").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("NEW"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.grade").doesNotExist())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.advice").isNotEmpty())
                .andExpect(jsonPath("$.windowDays").value(28));
    }

    @Test
    void note_un_compte_des_la_premiere_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        recordRecentRun(token);

        mockMvc.perform(get("/api/v1/me/rating?zone=Africa/Ouagadougou")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value(org.hamcrest.Matchers.not("NEW")))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.grade").isNotEmpty())
                .andExpect(jsonPath("$.title").isNotEmpty())
                // Les poids des composantes somment toujours a 100.
                .andExpect(jsonPath("$.components").isNotEmpty())
                .andExpect(jsonPath("$.nextTier").isNotEmpty());
    }

    @Test
    void rend_la_meme_note_a_deux_appels_successifs() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        recordRecentRun(token);

        String first = mockMvc.perform(get("/api/v1/me/rating").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/api/v1/me/rating").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Le calcul est deterministe : rien n'est stocke, rien n'est tire au sort.
        assertSameScore(first, second);
    }

    private void assertSameScore(String first, String second) throws Exception {
        org.assertj.core.api.Assertions.assertThat(json(first).get("score"))
                .isEqualTo(json(second).get("score"));
    }

    /**
     * Une seance datee d'aujourd'hui : la fenetre de notation est glissante, une
     * date fixe sortirait de la fenetre au fil des mois et le test deviendrait
     * faux tout seul.
     */
    private void recordRecentRun(String token) throws Exception {
        java.time.Instant start = java.time.Instant.now().minusSeconds(7_200);
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "startedAt": "%s", "endedAt": "%s",
                                 "distanceMeters": 8000}
                                """.formatted(start, start.plusSeconds(2_700))))
                .andExpect(status().isCreated());
    }

    /**
     * Le module {@code RATING} fait partie du socle ouvert a l'inscription, mais
     * il reste verrouillable comme les autres.
     */
    @TestPropertySource(properties = "pulsetrack.access.default-modules=WORKOUTS")
    static class Verrouillage extends AbstractApiIntegrationTest {

        @Test
        void repond_module_locked_quand_la_note_est_fermee() throws Exception {
            String token = registerUser();

            mockMvc.perform(get("/api/v1/me/rating").header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.module").value("RATING"));
        }
    }
}
