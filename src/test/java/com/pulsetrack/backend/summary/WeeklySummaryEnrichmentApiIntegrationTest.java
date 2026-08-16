package com.pulsetrack.backend.summary;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le cumul de la semaine et son appreciation.
 *
 * <p>C'est la reponse a « le cumul de km de la semaine ne se fait pas » : il se
 * faisait deja, mais rien ne le decoupait par jour et rien ne le commentait.
 */
class WeeklySummaryEnrichmentApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void rend_les_sept_jours_de_la_semaine_jours_vides_compris() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        recordRunOn(token, mondayOfThisWeek(), 5_000);

        mockMvc.perform(get("/api/v1/me/weekly-summary?zone=UTC").header("Authorization", token))
                .andExpect(status().isOk())
                // Une courbe dont les jours sans sport manquent donnerait
                // l'illusion d'une activite continue.
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[6].dayOfWeek").value("SUNDAY"))
                .andExpect(jsonPath("$.days[0].distanceMeters").value(5000.0))
                .andExpect(jsonPath("$.days[0].sessionCount").value(1))
                .andExpect(jsonPath("$.days[1].distanceMeters").value(0.0))
                .andExpect(jsonPath("$.days[1].sessionCount").value(0));
    }

    @Test
    void cumule_bien_la_distance_de_la_semaine() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        recordRunOn(token, mondayOfThisWeek(), 5_000);
        recordRunOn(token, mondayOfThisWeek().plusDays(2), 7_500);

        mockMvc.perform(get("/api/v1/me/weekly-summary?zone=UTC").header("Authorization", token))
                .andExpect(status().isOk())
                // En metres : c'est au client de diviser par mille.
                .andExpect(jsonPath("$.distanceMeters").value(12500.0))
                .andExpect(jsonPath("$.sessionCount").value(2))
                .andExpect(jsonPath("$.days[2].distanceMeters").value(7500.0));
    }

    @Test
    void commente_la_semaine_sans_accuser() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        recordRunOn(token, mondayOfThisWeek(), 5_000);

        mockMvc.perform(get("/api/v1/me/weekly-summary?zone=UTC").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appreciation.tier").isNotEmpty())
                .andExpect(jsonPath("$.appreciation.headline").isNotEmpty())
                .andExpect(jsonPath("$.appreciation.message").isNotEmpty());
    }

    @Test
    void compare_l_avancement_d_un_objectif_au_temps_ecoule() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "WEEKLY_DISTANCE", "targetValue": 20}
                                """))
                .andExpect(status().isCreated());

        recordRunOn(token, mondayOfThisWeek(), 5_000);

        mockMvc.perform(get("/api/v1/me/weekly-summary?zone=UTC").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].completionPercent").value(25.0))
                // La reference honnete : le pourcentage seul ne dit rien.
                .andExpect(jsonPath("$.goals[0].elapsedPercent").isNumber())
                .andExpect(jsonPath("$.goals[0].appreciation.tier").isNotEmpty())
                .andExpect(jsonPath("$.goals[0].appreciation.message").isNotEmpty());
    }

    /** Lundi de la semaine en cours, en UTC. */
    private LocalDate mondayOfThisWeek() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Seance a midi, sans trace : la distance declaree fait foi, ce qui permet
     * d'eprouver le cumul sur des chiffres exacts.
     */
    private void recordRunOn(String token, LocalDate day, double meters) throws Exception {
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(12 * 3_600L);
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "RUN", "startedAt": "%s", "endedAt": "%s",
                                 "distanceMeters": %s}
                                """.formatted(start, start.plusSeconds(1_800), meters)))
                .andExpect(status().isCreated());
    }
}
