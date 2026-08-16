package com.pulsetrack.backend.achievement;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat des felicitations, vu du client : <strong>{@code achievements} non
 * vide vaut confettis</strong>, et rien d'autre a calculer.
 */
class AchievementApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void celebre_la_premiere_seance_puis_le_record_de_distance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(run("2026-08-10T06:00:00Z", "2026-08-10T06:30:00Z", 5_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements.length()").value(1))
                .andExpect(jsonPath("$.achievements[0].kind").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.achievements[0].previousValue").doesNotExist())
                .andExpect(jsonPath("$.achievements[0].message").isNotEmpty());

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(run("2026-08-12T06:00:00Z", "2026-08-12T06:40:00Z", 8_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements[?(@.kind=='LONGEST_DISTANCE')].previousValue")
                        .value(org.hamcrest.Matchers.contains(5000.0)))
                .andExpect(jsonPath("$.achievements[?(@.kind=='LONGEST_DISTANCE')].improvement")
                        .value(org.hamcrest.Matchers.contains(3000.0)));
    }

    @Test
    void ne_celebre_rien_quand_la_seance_ne_bat_aucun_record() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        create(token, run("2026-08-10T06:00:00Z", "2026-08-10T06:40:00Z", 8_000));

        // Plus courte en distance, plus breve, et d'une allure plus lente :
        // aucune categorie n'est battue.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(run("2026-08-12T06:00:00Z", "2026-08-12T06:25:00Z", 4_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements.length()").value(0));
    }

    @Test
    void rend_les_memes_trophees_quand_la_seance_est_renvoyee() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        UUID id = UUID.randomUUID();
        String body = """
                {
                  "id": "%s",
                  "sportType": "RUN",
                  "startedAt": "2026-08-10T06:00:00Z",
                  "endedAt": "2026-08-10T06:30:00Z",
                  "distanceMeters": 5000
                }
                """.formatted(id);

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements.length()").value(1));

        // Renvoi apres coupure reseau : les felicitations n'explosent pas deux
        // fois, mais ne se perdent pas non plus.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements.length()").value(1))
                .andExpect(jsonPath("$.achievements[0].kind").value("FIRST_SESSION"));
    }

    @Test
    void rend_les_trophees_a_la_relecture_d_une_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String id = create(token, run("2026-08-10T06:00:00Z", "2026-08-10T06:30:00Z", 5_000));

        // De quoi badger l'historique sans requete supplementaire.
        mockMvc.perform(get("/api/v1/workouts/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements.length()").value(1));
    }

    @Test
    void separe_les_records_par_sport() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        create(token, run("2026-08-10T06:00:00Z", "2026-08-10T07:00:00Z", 30_000, "RIDE"));

        // Cinq kilometres a pied ne battent pas trente kilometres a velo : la
        // premiere course est une premiere seance, pas une deception.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(run("2026-08-11T06:00:00Z", "2026-08-11T06:30:00Z", 5_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.achievements[0].kind").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.achievements[0].sportType").value("RUN"));
    }

    @Test
    void expose_les_records_courants_par_sport() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        create(token, run("2026-08-10T06:00:00Z", "2026-08-10T06:30:00Z", 5_000));
        create(token, run("2026-08-12T06:00:00Z", "2026-08-12T07:00:00Z", 12_000));

        mockMvc.perform(get("/api/v1/workouts/records").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sportType").value("RUN"))
                .andExpect(jsonPath("$[0].sessionCount").value(2))
                .andExpect(jsonPath("$[0].records[?(@.kind=='LONGEST_DISTANCE')].value")
                        .value(org.hamcrest.Matchers.contains(12000.0)));
    }

    @Test
    void recalcule_les_records_courants_apres_suppression() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        create(token, run("2026-08-10T06:00:00Z", "2026-08-10T06:30:00Z", 5_000));
        String best = create(token, run("2026-08-12T06:00:00Z", "2026-08-12T07:00:00Z", 12_000));

        mockMvc.perform(delete("/api/v1/workouts/" + best).header("Authorization", token))
                .andExpect(status().isNoContent());

        // Un record garde en base afficherait indefiniment un chiffre que plus
        // rien ne justifie.
        mockMvc.perform(get("/api/v1/workouts/records").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionCount").value(1))
                .andExpect(jsonPath("$[0].records[?(@.kind=='LONGEST_DISTANCE')].value")
                        .value(org.hamcrest.Matchers.contains(5000.0)));
    }

    @Test
    void filtre_les_records_sur_un_sport() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        create(token, run("2026-08-10T06:00:00Z", "2026-08-10T06:30:00Z", 5_000));
        create(token, run("2026-08-11T06:00:00Z", "2026-08-11T07:00:00Z", 30_000, "RIDE"));

        mockMvc.perform(get("/api/v1/workouts/records?sport=RIDE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sportType").value("RIDE"));
    }

    private String create(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(response).get("summary").get("id").asText();
    }

    private String run(String startedAt, String endedAt, double meters) {
        return run(startedAt, endedAt, meters, "RUN");
    }

    /**
     * Seance sans trace GPS : la distance declaree fait foi. C'est ce qui permet
     * d'eprouver les records sur des chiffres exacts, sans dependre du filtre.
     */
    private String run(String startedAt, String endedAt, double meters, String sport) {
        return """
                {
                  "sportType": "%s",
                  "startedAt": "%s",
                  "endedAt": "%s",
                  "distanceMeters": %s
                }
                """.formatted(sport, startedAt, endedAt, meters);
    }
}
