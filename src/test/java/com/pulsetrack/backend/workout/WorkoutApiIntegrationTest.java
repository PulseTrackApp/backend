package com.pulsetrack.backend.workout;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat des seances : enregistrement, calcul serveur, historique, isolation
 * entre comptes et suppression.
 */
class WorkoutApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void enregistre_une_seance_et_calcule_ses_metriques_cote_serveur() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runWithTrackBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.id").isNotEmpty())
                .andExpect(jsonPath("$.summary.sportType").value("RUN"))
                // 1 km en 6 minutes : allure 6:00/km, 10 km/h, 77 kcal pour 70 kg.
                .andExpect(jsonPath("$.summary.averagePaceSecondsPerKm").value(360))
                .andExpect(jsonPath("$.summary.caloriesBurned").value(77))
                .andExpect(jsonPath("$.gpsPoints.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = json(response).get("summary").get("id").asText();

        mockMvc.perform(get("/api/v1/workouts/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpsPoints[0].position").value(0));
    }

    @Test
    void exige_un_profil_avant_d_enregistrer_une_seance() throws Exception {
        // Sans poids, l'estimation calorique serait inventee : on refuse plutot
        // que de stocker un chiffre faux dans l'historique.
        String token = registerUser();

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runWithTrackBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        "Renseignez votre profil (poids, taille) avant d'enregistrer une seance."));
    }

    @Test
    void refuse_une_seance_qui_se_termine_avant_de_commencer() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String body = """
                {
                  "sportType": "RUN",
                  "startedAt": "2026-08-10T07:00:00Z",
                  "endedAt": "2026-08-10T06:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Regle metier non respectee"));
    }

    @Test
    void refuse_une_coordonnee_hors_bornes() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String body = """
                {
                  "sportType": "RUN",
                  "startedAt": "2026-08-10T06:00:00Z",
                  "endedAt": "2026-08-10T06:06:00Z",
                  "gpsPoints": [
                    {"latitude": 120.0, "longitude": 2.3522, "recordedAt": "2026-08-10T06:00:00Z"},
                    {"latitude": 48.8566, "longitude": 2.3522, "recordedAt": "2026-08-10T06:06:00Z"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accepte_une_seance_sans_gps_avec_une_distance_declaree() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String body = """
                {
                  "sportType": "WALK",
                  "startedAt": "2026-08-10T06:00:00Z",
                  "endedAt": "2026-08-10T07:00:00Z",
                  "distanceMeters": 5000,
                  "perceivedEffort": 4,
                  "feeling": "GOOD",
                  "note": "Marche en salle"
                }
                """;

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.distanceMeters").value(5000.0))
                // MET 3,5 x 70 kg x 1 h = 245 kcal
                .andExpect(jsonPath("$.summary.caloriesBurned").value(245))
                .andExpect(jsonPath("$.summary.feeling").value("GOOD"))
                .andExpect(jsonPath("$.gpsPoints.length()").value(0));
    }

    @Test
    void liste_l_historique_pagine_et_filtre_par_sport() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runWithTrackBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/workouts").param("sport", "RIDE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void ne_donne_jamais_acces_a_la_seance_d_un_autre_compte() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 70.0);

        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runWithTrackBody()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String aliceWorkoutId = json(response).get("summary").get("id").asText();

        String bob = registerUser();
        saveProfile(bob, 80.0);

        // 404 et non 403 : repondre "interdit" confirmerait l'existence de la seance.
        mockMvc.perform(get("/api/v1/workouts/" + aliceWorkoutId).header("Authorization", bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/workouts/" + aliceWorkoutId).header("Authorization", bob))
                .andExpect(status().isNotFound());

        // La seance d'Alice est intacte.
        mockMvc.perform(get("/api/v1/workouts/" + aliceWorkoutId).header("Authorization", alice))
                .andExpect(status().isOk());

        // Et l'historique de Bob reste vide.
        mockMvc.perform(get("/api/v1/workouts").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void supprime_une_seance_et_son_trace() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runWithTrackBody()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = json(response).get("summary").get("id").asText();

        mockMvc.perform(delete("/api/v1/workouts/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/workouts/" + id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    /** 1 km plein nord depuis Paris, parcouru en 6 minutes. */
    private String runWithTrackBody() {
        return """
                {
                  "sportType": "RUN",
                  "startedAt": "2026-08-10T06:00:00Z",
                  "endedAt": "2026-08-10T06:06:00Z",
                  "perceivedEffort": 6,
                  "feeling": "GOOD",
                  "gpsPoints": [
                    {"latitude": 48.8566, "longitude": 2.3522, "altitude": 35.0,
                     "recordedAt": "2026-08-10T06:00:00Z"},
                    {"latitude": 48.8655931, "longitude": 2.3522, "altitude": 35.0,
                     "recordedAt": "2026-08-10T06:06:00Z"}
                  ]
                }
                """;
    }
}
