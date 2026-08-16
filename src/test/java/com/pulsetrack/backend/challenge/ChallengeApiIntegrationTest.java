package com.pulsetrack.backend.challenge;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat du mode defi, de la creation au verdict.
 *
 * <p>Le point a retenir pour le client : le plan remis a l'armement contient
 * seuils et messages, et se joue hors ligne. Le serveur n'est plus sollicite
 * pendant l'effort.
 */
class ChallengeApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String TEN_K = """
            {"sportType": "RUN", "targetDistanceMeters": 10000, "targetDurationSeconds": 3300}
            """;

    @Test
    void cree_un_defi_avec_son_allure_et_son_titre() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/challenges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEN_K))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requiredPaceSecondsPerKm").value(330))
                .andExpect(jsonPath("$.requiredSpeedKmh").value(10.9))
                // Titre engendre a partir de la cible : un champ vide serait pire.
                .andExpect(jsonPath("$.title").value("10 km en 55 min"))
                .andExpect(jsonPath("$.plan").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void ne_juge_pas_la_difficulte_sans_historique() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/challenges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEN_K))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.difficulty.level").value("INCONNU"))
                .andExpect(jsonPath("$.difficulty.referenceBasis").value("NONE"));
    }

    @Test
    void refuse_une_cible_absurde() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/challenges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "targetDistanceMeters": 50,
                                 "targetDurationSeconds": 3300}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refuse_une_date_limite_deja_passee() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/challenges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "targetDistanceMeters": 10000,
                                 "targetDurationSeconds": 3300, "expiresOn": "2020-01-01"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void remet_le_plan_complet_au_moment_d_armer_le_defi() throws Exception {
        String token = registerUser();
        String id = create(token, TEN_K);

        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/start")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.deadlineAt").isNotEmpty())
                .andExpect(jsonPath("$.plan.splits.length()").value(9))
                .andExpect(jsonPath("$.plan.splits[0].label").value("km 1"))
                // Les alertes d'echeance voyagent avec leurs messages : le
                // telephone les joue seul, meme en mode avion.
                .andExpect(jsonPath("$.plan.cues[?(@.kind=='DEADLINE_ALERT')]")
                        .value(org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.plan.cues[0].message").isNotEmpty());
    }

    @Test
    void n_autorise_qu_un_seul_defi_arme_a_la_fois() throws Exception {
        String token = registerUser();
        String first = create(token, TEN_K);
        String second = create(token, TEN_K);

        mockMvc.perform(post("/api/v1/me/challenges/" + first + "/start")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/challenges/" + second + "/start")
                        .header("Authorization", token))
                .andExpect(status().isConflict());
    }

    @Test
    void libere_la_place_apres_un_abandon() throws Exception {
        String token = registerUser();
        String first = create(token, TEN_K);
        String second = create(token, TEN_K);

        mockMvc.perform(post("/api/v1/me/challenges/" + first + "/start")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/challenges/" + first + "/abandon")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"));

        mockMvc.perform(post("/api/v1/me/challenges/" + second + "/start")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void rend_un_point_d_etape_sans_modifier_le_defi() throws Exception {
        String token = registerUser();
        String id = create(token, TEN_K);
        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/start").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"elapsedSeconds": 1200, "distanceMeters": 3400}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(2100))
                .andExpect(jsonPath("$.remainingDistanceMeters").value(6600.0))
                .andExpect(jsonPath("$.onTrack").value(false))
                .andExpect(jsonPath("$.alertLevel").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        // L'appel n'ecrit rien : le defi est toujours en cours.
        mockMvc.perform(get("/api/v1/me/challenges/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void regle_le_defi_en_meme_temps_que_la_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String id = create(token, TEN_K);
        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/start").header("Authorization", token))
                .andExpect(status().isOk());

        // Un seul aller-retour a l'arrivee, ce qui compte quand le reseau revient
        // a peine.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "startedAt": "2026-08-14T06:00:00Z",
                                 "endedAt": "2026-08-14T06:54:40Z", "distanceMeters": 10120,
                                 "challengeId": "%s"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeResult.succeeded").value(true))
                .andExpect(jsonPath("$.challengeResult.timeMarginSeconds").value(20))
                .andExpect(jsonPath("$.challengeResult.celebrate").value(true))
                .andExpect(jsonPath("$.challengeResult.appreciation.tier").value("EXCELLENT"));

        mockMvc.perform(get("/api/v1/me/challenges/" + id).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.succeeded").value(true))
                // Une fois joue, c'est le resultat qui compte, pas l'avis d'avant.
                .andExpect(jsonPath("$.difficulty").doesNotExist());
    }

    @Test
    void declare_l_echec_quand_l_echeance_est_depassee() throws Exception {
        String token = registerUser();
        String id = create(token, TEN_K);

        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/complete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"distanceMeters": 10000, "durationSeconds": 3600}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.result.succeeded").value(false))
                .andExpect(jsonPath("$.result.appreciation.headline")
                        .value("Distance faite, échéance dépassée"))
                // Le message reste tourne vers la prochaine tentative.
                .andExpect(jsonPath("$.result.appreciation.advice").isNotEmpty());
    }

    @Test
    void refuse_de_regler_deux_fois_le_meme_defi() throws Exception {
        String token = registerUser();
        String id = create(token, TEN_K);

        String body = """
                {"distanceMeters": 10000, "durationSeconds": 3200}
                """;

        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/complete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/challenges/" + id + "/complete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void filtre_la_liste_sur_plusieurs_statuts() throws Exception {
        String token = registerUser();
        String active = create(token, TEN_K);
        create(token, TEN_K);
        mockMvc.perform(post("/api/v1/me/challenges/" + active + "/start")
                        .header("Authorization", token)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/challenges?status=ACTIVE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/me/challenges?status=DRAFT,ACTIVE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void isole_les_defis_entre_comptes() throws Exception {
        String owner = registerUser();
        String id = create(owner, TEN_K);

        String intruder = registerUser();

        mockMvc.perform(get("/api/v1/me/challenges/" + id).header("Authorization", intruder))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/me/challenges/" + id).header("Authorization", intruder))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/challenges/" + id).header("Authorization", owner))
                .andExpect(status().isOk());
    }

    private String create(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/me/challenges")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asText();
    }
}
