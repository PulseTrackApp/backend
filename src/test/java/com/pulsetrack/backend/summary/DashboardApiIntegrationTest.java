package com.pulsetrack.backend.summary;

import java.time.LocalDate;
import java.time.ZoneOffset;

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
 * Contrat des releves physiques, des objectifs et du bilan hebdomadaire.
 */
class DashboardApiIntegrationTest extends AbstractApiIntegrationTest {

    // ----- Releves physiques -------------------------------------------------

    @Test
    void enregistre_un_releve_et_calcule_la_progression() throws Exception {
        String token = registerUser();
        saveProfile(token, 80.0);

        putCheckIn(token, LocalDate.now().minusWeeks(4), 80.0).andExpect(status().isOk());
        putCheckIn(token, LocalDate.now().minusWeeks(2), 79.0).andExpect(status().isOk());
        putCheckIn(token, LocalDate.now(), 78.0).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/body-checkins/progress").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInCount").value(3))
                .andExpect(jsonPath("$.startWeightKg").value(80.0))
                .andExpect(jsonPath("$.currentWeightKg").value(78.0))
                .andExpect(jsonPath("$.totalChangeKg").value(-2.0))
                .andExpect(jsonPath("$.averageWeeklyChangeKg").value(-0.5))
                .andExpect(jsonPath("$.trend").value("LOSING"))
                // 78 / 1,78^2 = 24,6
                .andExpect(jsonPath("$.bmiCategory").value("NORMAL"))
                // La serie est rendue du plus ancien au plus recent, prete a tracer.
                .andExpect(jsonPath("$.series[0].weightKg").value(80.0));
    }

    @Test
    void remplace_le_releve_du_meme_jour_au_lieu_d_en_creer_un_second() throws Exception {
        String token = registerUser();
        saveProfile(token, 80.0);
        LocalDate today = LocalDate.now();

        putCheckIn(token, today, 80.0).andExpect(status().isOk());
        putCheckIn(token, today, 79.4).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/body-checkins/progress").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInCount").value(1))
                .andExpect(jsonPath("$.currentWeightKg").value(79.4));
    }

    @Test
    void reporte_le_dernier_poids_pese_sur_le_profil() throws Exception {
        // Le poids du profil sert au calcul des calories : il doit suivre la balance.
        String token = registerUser();
        saveProfile(token, 80.0);

        putCheckIn(token, LocalDate.now(), 76.2).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(76.2));
    }

    @Test
    void ne_reporte_pas_un_releve_anterieur_sur_le_profil() throws Exception {
        String token = registerUser();
        saveProfile(token, 80.0);

        putCheckIn(token, LocalDate.now(), 76.0).andExpect(status().isOk());
        // Rattrapage d'un oubli de la semaine passee : ne doit pas ecraser le poids du jour.
        putCheckIn(token, LocalDate.now().minusWeeks(1), 79.0).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg").value(76.0));
    }

    @Test
    void refuse_un_releve_date_dans_le_futur() throws Exception {
        String token = registerUser();

        putCheckIn(token, LocalDate.now().plusDays(1), 78.0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.checkinDate").isNotEmpty());
    }

    @Test
    void ne_montre_pas_les_releves_d_un_autre_compte() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 80.0);
        putCheckIn(alice, LocalDate.now(), 78.0).andExpect(status().isOk());

        String bob = registerUser();
        mockMvc.perform(get("/api/v1/me/body-checkins/progress").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInCount").value(0))
                .andExpect(jsonPath("$.trend").value("NOT_ENOUGH_DATA"));
    }

    // ----- Objectifs ---------------------------------------------------------

    @Test
    void fixe_un_objectif_et_refuse_le_doublon_du_meme_type() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_DISTANCE", 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unit").value("km"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_DISTANCE", 30)))
                .andExpect(status().isConflict());
    }

    @Test
    void archive_un_objectif_puis_autorise_un_nouveau_du_meme_type() throws Exception {
        String token = registerUser();

        String created = mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_SESSIONS", 3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String goalId = json(created).get("id").asText();

        mockMvc.perform(post("/api/v1/me/goals/" + goalId + "/archive").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // L'index partiel ne portant que sur les objectifs actifs, la place est libre.
        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_SESSIONS", 4)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/me/goals").param("activeOnly", "false").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void refuse_une_cible_nulle_ou_negative() throws Exception {
        String token = registerUser();

        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_DISTANCE", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetValue").isNotEmpty());
    }

    @Test
    void ne_donne_pas_acces_a_l_objectif_d_un_autre_compte() throws Exception {
        String alice = registerUser();
        String created = mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_CALORIES", 2000)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String goalId = json(created).get("id").asText();

        String bob = registerUser();
        mockMvc.perform(delete("/api/v1/me/goals/" + goalId).header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    // ----- Bilan hebdomadaire ------------------------------------------------

    @Test
    void agrege_la_semaine_et_mesure_la_progression_des_objectifs() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("WEEKLY_DISTANCE", 4)))
                .andExpect(status().isCreated());

        // Deux courses de 1 km cette semaine : lundi et mardi.
        LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY);
        createOneKilometreRun(token, monday);
        createOneKilometreRun(token, monday.plusDays(1));

        mockMvc.perform(get("/api/v1/me/weekly-summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(monday.toString()))
                .andExpect(jsonPath("$.weekEnd").value(monday.plusDays(6).toString()))
                .andExpect(jsonPath("$.sessionCount").value(2))
                .andExpect(jsonPath("$.distanceMeters").value(2000.0))
                .andExpect(jsonPath("$.caloriesBurned").value(154))
                .andExpect(jsonPath("$.goals.length()").value(1))
                .andExpect(jsonPath("$.goals[0].currentValue").value(2.0))
                .andExpect(jsonPath("$.goals[0].completionPercent").value(50.0))
                .andExpect(jsonPath("$.goals[0].remaining").value(2.0))
                .andExpect(jsonPath("$.goals[0].achieved").value(false));
    }

    @Test
    void compte_les_jours_consecutifs_d_activite() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createOneKilometreRun(token, today);
        createOneKilometreRun(token, today.minusDays(1));
        createOneKilometreRun(token, today.minusDays(2));

        mockMvc.perform(get("/api/v1/me/weekly-summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDayStreak").value(3));
    }

    @Test
    void renvoie_une_semaine_vide_sans_planter_et_sans_pourcentage_invente() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/weekly-summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCount").value(0))
                .andExpect(jsonPath("$.distanceMeters").value(0.0))
                .andExpect(jsonPath("$.activeDayStreak").value(0))
                .andExpect(jsonPath("$.goals.length()").value(0))
                // Semaine precedente vide : aucune variation relative calculable.
                .andExpect(jsonPath("$.previousWeek.distanceChangePercent").doesNotExist());
    }

    @Test
    void refuse_un_fuseau_horaire_inconnu() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/weekly-summary")
                        .param("zone", "Mars/Olympus")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Date ou fuseau horaire invalide."));
    }

    @Test
    void accepte_un_fuseau_explicite() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/weekly-summary")
                        .param("zone", "Africa/Ouagadougou")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Africa/Ouagadougou"));
    }

    // ----- Utilitaires -------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions putCheckIn(String token,
                                                                          LocalDate date,
                                                                          double weightKg) throws Exception {
        String body = """
                {"checkinDate": "%s", "weightKg": %s, "energyLevel": 4}
                """.formatted(date, weightKg);
        return mockMvc.perform(put("/api/v1/me/body-checkins")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String goalBody(String type, double targetValue) {
        return """
                {"type": "%s", "targetValue": %s}
                """.formatted(type, targetValue);
    }

    /** 1 km en 6 minutes le jour indique, a 6 h UTC. */
    private void createOneKilometreRun(String token, LocalDate day) throws Exception {
        String body = """
                {
                  "sportType": "RUN",
                  "startedAt": "%sT06:00:00Z",
                  "endedAt": "%sT06:06:00Z",
                  "gpsPoints": [
                    {"latitude": 48.8566, "longitude": 2.3522, "recordedAt": "%sT06:00:00Z"},
                    {"latitude": 48.8655931, "longitude": 2.3522, "recordedAt": "%sT06:06:00Z"}
                  ]
                }
                """.formatted(day, day, day, day);

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
