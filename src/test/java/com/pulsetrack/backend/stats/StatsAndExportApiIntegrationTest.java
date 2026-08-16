package com.pulsetrack.backend.stats;

import java.time.LocalDate;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat des statistiques par periode et de l'export complet.
 */
class StatsAndExportApiIntegrationTest extends AbstractApiIntegrationTest {

    // ----- Statistiques ------------------------------------------------------

    @Test
    void agrege_les_statistiques_du_mois_avec_serie_continue() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        createRun(token, LocalDate.of(2026, 6, 3));
        createRun(token, LocalDate.of(2026, 6, 4));
        createRun(token, LocalDate.of(2026, 6, 20));

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "MONTH")
                        .param("reference", "2026-06-15")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.start").value("2026-06-01"))
                .andExpect(jsonPath("$.end").value("2026-06-30"))
                .andExpect(jsonPath("$.totals.sessionCount").value(3))
                .andExpect(jsonPath("$.totals.activeDays").value(3))
                .andExpect(jsonPath("$.totals.distanceMeters").value(3000.0))
                // Juin compte 30 jours : la serie doit tous les contenir, meme vides.
                .andExpect(jsonPath("$.series.length()").value(30))
                .andExpect(jsonPath("$.series[0].totals.sessionCount").value(0))
                .andExpect(jsonPath("$.series[2].totals.sessionCount").value(1))
                .andExpect(jsonPath("$.bySport.length()").value(1))
                .andExpect(jsonPath("$.bySport[0].sport").value("RUN"))
                .andExpect(jsonPath("$.bySport[0].distanceSharePercent").value(100.0));
    }

    /**
     * L'onglet « semaine » de l'ecran des performances. Meme code que le mois,
     * seules les bornes changent — mais c'est justement la que se logeait le
     * doute : la semaine commence-t-elle bien le lundi, et le dimanche est-il
     * inclus ?
     */
    @Test
    void agrege_la_semaine_du_lundi_au_dimanche_inclus() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        // 2026-06-08 est un lundi, 2026-06-14 le dimanche qui le suit.
        createRun(token, LocalDate.of(2026, 6, 7));   // dimanche precedent : dehors
        createRun(token, LocalDate.of(2026, 6, 8));   // lundi : dedans
        createRun(token, LocalDate.of(2026, 6, 14));  // dimanche : dedans
        createRun(token, LocalDate.of(2026, 6, 15));  // lundi suivant : dehors

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "WEEK")
                        .param("reference", "2026-06-10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEK"))
                .andExpect(jsonPath("$.start").value("2026-06-08"))
                .andExpect(jsonPath("$.end").value("2026-06-14"))
                .andExpect(jsonPath("$.totals.sessionCount").value(2))
                .andExpect(jsonPath("$.totals.distanceMeters").value(2000.0))
                // Sept jours, jours vides compris.
                .andExpect(jsonPath("$.series.length()").value(7))
                .andExpect(jsonPath("$.series[0].totals.sessionCount").value(1))
                .andExpect(jsonPath("$.series[1].totals.sessionCount").value(0))
                .andExpect(jsonPath("$.series[6].totals.sessionCount").value(1))
                // La semaine precedente sert de comparaison : la sortie du 7 juin.
                .andExpect(jsonPath("$.previousPeriod.sessionCount").value(1))
                .andExpect(jsonPath("$.records.longestDistanceMeters").value(1000.0));
    }

    @Test
    void agrege_l_annee_par_mois() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        createRun(token, LocalDate.of(2026, 3, 10));

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "YEAR")
                        .param("reference", "2026-08-01")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-01-01"))
                .andExpect(jsonPath("$.end").value("2026-12-31"))
                .andExpect(jsonPath("$.series.length()").value(12))
                .andExpect(jsonPath("$.series[2].totals.sessionCount").value(1))
                .andExpect(jsonPath("$.totals.sessionCount").value(1));
    }

    @Test
    void couvre_tout_l_historique_depuis_la_premiere_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        createRun(token, LocalDate.of(2025, 11, 2));
        createRun(token, LocalDate.of(2026, 4, 18));

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "LIFETIME")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2025-11-02"))
                .andExpect(jsonPath("$.totals.sessionCount").value(2))
                // Rien ne precede le debut : aucune comparaison possible.
                .andExpect(jsonPath("$.previousPeriod").doesNotExist());
    }

    @Test
    void compare_avec_la_periode_precedente() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        createRun(token, LocalDate.of(2026, 6, 1));
        createRun(token, LocalDate.of(2026, 7, 1));
        createRun(token, LocalDate.of(2026, 7, 2));

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "MONTH")
                        .param("reference", "2026-07-15")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sessionCount").value(2))
                .andExpect(jsonPath("$.previousPeriod.sessionCount").value(1));
    }

    @Test
    void integre_l_evolution_du_poids_dans_les_statistiques() throws Exception {
        String token = registerUser();
        saveProfile(token, 80.0);

        putCheckIn(token, LocalDate.of(2026, 6, 1), 80.0, 92.0);
        putCheckIn(token, LocalDate.of(2026, 6, 15), 79.0, 91.0);
        putCheckIn(token, LocalDate.of(2026, 6, 29), 78.0, 89.5);
        // Hors periode : ne doit pas fausser le mois de juin.
        putCheckIn(token, LocalDate.of(2026, 7, 5), 77.0, 89.0);

        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "MONTH")
                        .param("reference", "2026-06-10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.checkInCount").value(3))
                .andExpect(jsonPath("$.body.startWeightKg").value(80.0))
                .andExpect(jsonPath("$.body.endWeightKg").value(78.0))
                .andExpect(jsonPath("$.body.changeKg").value(-2.0))
                .andExpect(jsonPath("$.body.minWeightKg").value(78.0))
                .andExpect(jsonPath("$.body.maxWeightKg").value(80.0))
                .andExpect(jsonPath("$.body.trend").value("LOSING"))
                // Tour de taille : 92 puis 89,5, soit -2,5 cm
                .andExpect(jsonPath("$.body.waistChangeCm").value(-2.5))
                .andExpect(jsonPath("$.body.series.length()").value(3));
    }

    @Test
    void renvoie_des_statistiques_vides_sans_planter() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/stats").param("period", "WEEK").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sessionCount").value(0))
                .andExpect(jsonPath("$.bySport.length()").value(0))
                .andExpect(jsonPath("$.series.length()").value(7))
                .andExpect(jsonPath("$.records.bestPaceSecondsPerKm").doesNotExist())
                .andExpect(jsonPath("$.body.checkInCount").value(0));
    }

    @Test
    void refuse_une_periode_inconnue() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/stats").param("period", "DECENNIE").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ne_montre_pas_les_statistiques_d_un_autre_compte() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 70.0);
        createRun(alice, LocalDate.of(2026, 6, 3));

        String bob = registerUser();
        mockMvc.perform(get("/api/v1/me/stats")
                        .param("period", "MONTH")
                        .param("reference", "2026-06-15")
                        .header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sessionCount").value(0));
    }

    // ----- Export ------------------------------------------------------------

    @Test
    void exporte_l_integralite_des_donnees_avec_les_traces() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.5);
        createRun(token, LocalDate.of(2026, 6, 3));
        putCheckIn(token, LocalDate.of(2026, 6, 3), 72.5, 90.0);
        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "WEEKLY_DISTANCE", "targetValue": 20}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.formatVersion").value(2))
                .andExpect(jsonPath("$.profile.displayName").value("Nicolas"))
                .andExpect(jsonPath("$.workouts.length()").value(1))
                // Le trace complet doit sortir : un parcours ampute n'est pas rejouable.
                .andExpect(jsonPath("$.workouts[0].gpsPoints.length()").value(2))
                // Les trophees partent avec les seances : sans eux, l'archive ne
                // dirait pas ce que ces sorties ont represente.
                .andExpect(jsonPath("$.workouts[0].achievements").isArray())
                .andExpect(jsonPath("$.bodyCheckIns.length()").value(1))
                .andExpect(jsonPath("$.goals.length()").value(1))
                // Un domaine oublie ici rendrait l'archive silencieusement
                // incomplete : les deux nouveaux doivent y figurer, meme vides.
                .andExpect(jsonPath("$.routes").isArray())
                .andExpect(jsonPath("$.challenges").isArray());
    }

    @Test
    void l_export_ne_contient_ni_mot_de_passe_ni_cle_api() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.5);

        String archive = mockMvc.perform(get("/api/v1/me/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(archive)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("encryptedApiKey")
                .doesNotContain("apiKey");
    }

    @Test
    void l_export_d_un_compte_vide_reste_valide() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/export").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workouts.length()").value(0))
                // Profil jamais rempli : champ absent, pas d'erreur.
                .andExpect(jsonPath("$.profile").doesNotExist());
    }

    @Test
    void l_export_ne_melange_pas_les_comptes() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 70.0);
        createRun(alice, LocalDate.of(2026, 6, 3));

        String bob = registerUser();
        mockMvc.perform(get("/api/v1/me/export").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workouts.length()").value(0));
    }

    // ----- Utilitaires -------------------------------------------------------

    /** 1 km en 6 minutes le jour indique. */
    private void createRun(String token, LocalDate day) throws Exception {
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

    private void putCheckIn(String token, LocalDate date, double weightKg, double waistCm) throws Exception {
        mockMvc.perform(put("/api/v1/me/body-checkins")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checkinDate": "%s", "weightKg": %s, "waistCm": %s}
                                """.formatted(date, weightKg, waistCm)))
                .andExpect(status().isOk());
    }
}
