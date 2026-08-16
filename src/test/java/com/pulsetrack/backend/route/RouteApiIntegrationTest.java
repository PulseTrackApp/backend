package com.pulsetrack.backend.route;

import java.util.ArrayList;
import java.util.List;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contrat des parcours rejouables : enregistrer un circuit, le reprendre, et
 * savoir si on a fait mieux que la derniere fois.
 */
class RouteApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void enregistre_un_parcours_a_partir_d_une_seance_tracee() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String workoutId = createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800);

        mockMvc.perform(post("/api/v1/me/routes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workoutId": "%s", "name": "Boucle du barrage"}
                                """.formatted(workoutId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Boucle du barrage"))
                .andExpect(jsonPath("$.sportType").value("RUN"))
                .andExpect(jsonPath("$.sourceWorkoutId").value(workoutId))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points[0].cumulativeDistanceMeters").value(0.0));
    }

    @Test
    void refuse_un_parcours_sans_trace_exploitable() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        // Seance en salle : distance declaree, aucun point GPS.
        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "OTHER", "startedAt": "2026-08-10T06:00:00Z",
                                 "endedAt": "2026-08-10T07:00:00Z", "distanceMeters": 3000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String workoutId = json(response).get("summary").get("id").asText();

        mockMvc.perform(post("/api/v1/me/routes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workoutId": "%s", "name": "Salle"}
                                """.formatted(workoutId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Règle métier non respectée"));
    }

    @Test
    void refuse_deux_parcours_du_meme_nom() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String routeId = createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800),
                "Boucle du barrage");

        String second = createTrackedRun(token, "2026-08-11T06:00:00Z", 1_800);

        // La casse est ignoree : deux circuits du meme nom rendraient l'ecran de
        // choix indechiffrable.
        mockMvc.perform(post("/api/v1/me/routes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workoutId": "%s", "name": "boucle du barrage"}
                                """.formatted(second)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/me/routes/" + routeId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void compare_un_passage_aux_precedents_au_moment_de_l_enregistrer() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String routeId = createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800),
                "Boucle du barrage");

        // Premier passage rattache au parcours : rien a comparer.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackedRun("2026-08-12T06:00:00Z", 1_800, routeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeComparison.attemptNumber").value(1))
                .andExpect(jsonPath("$.routeComparison.isNewBest").value(false))
                .andExpect(jsonPath("$.routeComparison.headline").value("Premier passage"))
                .andExpect(jsonPath("$.routeComparison.bestPreviousDurationSeconds").doesNotExist());

        // Second passage, plus rapide : nouveau meilleur temps, et un trophee.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackedRun("2026-08-14T06:00:00Z", 1_500, routeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeComparison.attemptNumber").value(2))
                .andExpect(jsonPath("$.routeComparison.isNewBest").value(true))
                .andExpect(jsonPath("$.routeComparison.rank").value(1))
                // Ecart negatif : plus rapide, comme sur un chronometre.
                .andExpect(jsonPath("$.routeComparison.deltaSecondsVsBest").value(
                        org.hamcrest.Matchers.lessThan(0)))
                .andExpect(jsonPath("$.achievements[?(@.kind=='BEST_ROUTE_TIME')]")
                        .value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void classe_les_passages_du_plus_rapide_au_plus_lent() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String routeId = createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800),
                "Boucle du barrage");

        createTrackedRun(token, "2026-08-12T06:00:00Z", 1_800, routeId);
        createTrackedRun(token, "2026-08-13T06:00:00Z", 1_500, routeId);
        createTrackedRun(token, "2026-08-14T06:00:00Z", 1_650, routeId);

        mockMvc.perform(get("/api/v1/me/routes/" + routeId + "/attempts")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].isBest").value(true))
                .andExpect(jsonPath("$[0].deltaSecondsVsBest").value(0))
                .andExpect(jsonPath("$[2].deltaSecondsVsBest").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void n_expose_pas_le_trace_dans_la_liste_paginee() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800), "Boucle");

        // Trois cents points par ligne de liste couteraient un demi-megaoctet
        // pour dessiner vingt vignettes.
        mockMvc.perform(get("/api/v1/me/routes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].points").doesNotExist())
                .andExpect(jsonPath("$.content[0].pointCount").isNumber());
    }

    @Test
    void renomme_un_parcours_sans_toucher_a_sa_geometrie() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String routeId = createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800),
                "Ancien nom");

        mockMvc.perform(put("/api/v1/me/routes/" + routeId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Boucle du barrage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Boucle du barrage"))
                .andExpect(jsonPath("$.points").isArray());
    }

    @Test
    void supprimer_un_parcours_ne_supprime_aucune_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String routeId = createRoute(token, createTrackedRun(token, "2026-08-10T06:00:00Z", 1_800),
                "Boucle");
        String attempt = createTrackedRun(token, "2026-08-12T06:00:00Z", 1_800, routeId);

        mockMvc.perform(delete("/api/v1/me/routes/" + routeId).header("Authorization", token))
                .andExpect(status().isNoContent());

        // La sortie a bien eu lieu : elle perd son rattachement, pas son existence.
        mockMvc.perform(get("/api/v1/workouts/" + attempt).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/routes/" + routeId).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_de_rattacher_une_seance_au_parcours_de_quelqu_un_d_autre() throws Exception {
        String owner = registerUser();
        saveProfile(owner, 70.0);
        String routeId = createRoute(owner, createTrackedRun(owner, "2026-08-10T06:00:00Z", 1_800),
                "Boucle privee");

        String intruder = registerUser();
        saveProfile(intruder, 70.0);

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackedRun("2026-08-12T06:00:00Z", 1_800, routeId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/me/routes/" + routeId).header("Authorization", intruder))
                .andExpect(status().isNotFound());
    }

    private String createRoute(String token, String workoutId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/me/routes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workoutId": "%s", "name": "%s"}
                                """.formatted(workoutId, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asText();
    }

    private String createTrackedRun(String token, String startedAt, long seconds) throws Exception {
        return createTrackedRun(token, startedAt, seconds, null);
    }

    private String createTrackedRun(String token, String startedAt, long seconds, String routeId)
            throws Exception {
        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackedRun(startedAt, seconds, routeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("summary").get("id").asText();
    }

    /**
     * Une trace de trente points le long d'un meridien, espaces de cent metres et
     * parcourus en {@code seconds}.
     *
     * <p>L'espacement compte : le classement des passages porte sur le temps
     * <strong>en mouvement</strong>, et sous 0,5 m/s le calculateur considere
     * l'utilisateur a l'arret. Une trace trop lente rendrait des durees en
     * mouvement toutes nulles, et toutes les tentatives seraient ex aequo.
     */
    private String trackedRun(String startedAt, long seconds, String routeId) {
        java.time.Instant start = java.time.Instant.parse(startedAt);
        int points = 30;
        List<String> gps = new ArrayList<>(points);
        for (int index = 0; index < points; index++) {
            double latitude = 12.3714 + index * 100d / 111_320d;
            java.time.Instant at = start.plusSeconds(seconds * index / (points - 1));
            gps.add("""
                    {"latitude": %s, "longitude": -1.5197, "accuracy": 4.0, "recordedAt": "%s"}
                    """.formatted(latitude, at));
        }

        String route = routeId == null ? "" : "\"routeId\": \"%s\",".formatted(routeId);
        return """
                {
                  %s
                  "sportType": "RUN",
                  "startedAt": "%s",
                  "endedAt": "%s",
                  "gpsPoints": [%s]
                }
                """.formatted(route, start, start.plusSeconds(seconds), String.join(",", gps));
    }
}
