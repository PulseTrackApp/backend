package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recalcul des metriques des seances deja enregistrees.
 *
 * <p>Les metriques sont figees en base a l'enregistrement : une correction de
 * formule ne repare rien de l'historique sans ce geste. C'est ce qui a laisse
 * une marche afficher un pic de 23,5 km/h longtemps apres que le calcul eut ete
 * corrige.
 *
 * <p>L'adresse d'administration est la meme que celle de {@code AdminApiIntegrationTest}
 * a dessein : Spring met en cache un contexte par jeu de proprietes, et en
 * reutiliser un evite de demarrer un second PostgreSQL pour trois tests.
 */
@TestPropertySource(properties = "pulsetrack.access.admin-email=chef@pulsetrack.test")
class WorkoutMetricsRecomputeApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String ADMIN_EMAIL = "chef@pulsetrack.test";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void repare_une_seance_dont_les_metriques_ont_ete_abimees() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.0);
        UUID seance = enregistreUneCourse(token);

        double distanceJuste = distanceEnBase(seance);
        double picJuste = picEnBase(seance);
        assertThat(distanceJuste).isGreaterThan(0);

        // On remet les chiffres qu'aurait produits l'ancienne formule.
        jdbc.update("update workout_sessions set distance_meters = ?, max_speed_kmh = ? where id = ?",
                distanceJuste * 1.3, 23.5, seance);

        recompute().andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsUpdated").value(1));

        assertThat(distanceEnBase(seance)).isEqualTo(distanceJuste);
        assertThat(picEnBase(seance)).isEqualTo(picJuste);
    }

    /**
     * Rejouer l'operation ne doit plus rien toucher : c'est ce qui permet de la
     * relancer sans crainte apres un doute, ou de la brancher sur un
     * redemarrage.
     */
    @Test
    void ne_touche_plus_rien_au_second_passage() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.0);
        enregistreUneCourse(token);

        recompute().andExpect(status().isOk());

        recompute().andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsUpdated").value(0))
                .andExpect(jsonPath("$.sessionsExamined").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void reste_ferme_a_un_utilisateur_ordinaire() throws Exception {
        mockMvc.perform(post("/api/v1/admin/workouts/recompute-metrics")
                        .header("Authorization", registerUser()))
                .andExpect(status().isForbidden());
    }

    // --- utilitaires -------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions recompute() throws Exception {
        return mockMvc.perform(post("/api/v1/admin/workouts/recompute-metrics")
                .header("Authorization", adminToken()));
    }

    /** Course d'une minute, un point toutes les trois secondes, tracee au nord. */
    private UUID enregistreUneCourse(String token) throws Exception {
        Instant depart = Instant.parse("2026-08-12T05:00:00Z");
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            if (i > 0) {
                points.append(",");
            }
            points.append("""
                    {"latitude": %s, "longitude": -1.5197, "accuracy": 4.0, "speed": 3.0,
                     "recordedAt": "%s"}
                    """.formatted(
                    12.3714 + i * 9 * 0.000009,
                    depart.plusSeconds(3L * i)));
        }

        String body = """
                {
                  "sportType": "RUN",
                  "startedAt": "%s",
                  "endedAt": "%s",
                  "gpsPoints": [%s]
                }
                """.formatted(depart, depart.plusSeconds(60), points);

        String response = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(json(response).get("summary").get("id").asText());
    }

    private double distanceEnBase(UUID seance) {
        return jdbc.queryForObject(
                "select distance_meters from workout_sessions where id = ?", Double.class, seance);
    }

    private double picEnBase(UUID seance) {
        return jdbc.queryForObject(
                "select max_speed_kmh from workout_sessions where id = ?", Double.class, seance);
    }

    /** S'inscrit ou se connecte : l'adresse de l'administrateur est fixe. */
    private String adminToken() throws Exception {
        String body = """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(ADMIN_EMAIL);

        String chemin = existeDeja() ? "/api/v1/auth/login" : "/api/v1/auth/register";
        String response = mockMvc.perform(post(chemin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }

    private boolean existeDeja() {
        Integer compte = jdbc.queryForObject(
                "select count(*) from users where email = ?", Integer.class, ADMIN_EMAIL);
        return compte != null && compte > 0;
    }

    /** Verifie au passage que la route n'est pas verrouillable par module. */
    @Test
    void n_est_pas_soumise_au_verrouillage_par_module() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules").header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }
}
