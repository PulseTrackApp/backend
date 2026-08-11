package com.pulsetrack.backend.workout;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renvoi d'une seance deja enregistree.
 *
 * <p>Le cas vise n'est pas theorique : avec un suivi en arriere-plan, une course
 * d'une heure se televerse quand le reseau revient, souvent mal. Si la reponse
 * se perd apres que le serveur a enregistre, le mobile reessaie — et sans
 * identifiant fourni par le client, il cree une seconde seance identique. Les
 * statistiques comptent alors deux fois la meme sortie.
 */
class WorkoutIdempotencyApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void enregistre_la_seance_au_premier_envoi() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String id = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.id").value(id));
    }

    @Test
    void ne_cree_pas_de_doublon_quand_le_mobile_renvoie_la_meme_seance() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String id = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isCreated());

        // Deuxieme envoi : 200 et non 201, ce qui dit au mobile que son premier
        // envoi avait abouti malgre la reponse perdue.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(id));

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void rend_la_seance_deja_enregistree_sans_la_recalculer() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);
        String id = UUID.randomUUID().toString();

        String first = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Meme distance, memes calories : c'est bien l'enregistrement d'origine
        // qui est rendu, pas un nouveau calcul sur les memes donnees.
        org.assertj.core.api.Assertions.assertThat(json(replay).get("summary").get("distanceMeters"))
                .isEqualTo(json(first).get("summary").get("distanceMeters"));
        org.assertj.core.api.Assertions.assertThat(json(replay).get("summary").get("caloriesBurned"))
                .isEqualTo(json(first).get("summary").get("caloriesBurned"));
    }

    @Test
    void refuse_un_identifiant_appartenant_a_un_autre_compte() throws Exception {
        String alice = registerUser();
        saveProfile(alice, 70.0);
        String bob = registerUser();
        saveProfile(bob, 80.0);
        String id = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isCreated());

        // Sans ce refus, l'insertion violerait la cle primaire et remonterait en
        // 500. Le message ne revele pas a qui appartient l'identifiant.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(id)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void genere_un_identifiant_quand_le_client_n_en_fournit_pas() throws Exception {
        String token = registerUser();
        saveProfile(token, 70.0);

        // Retrocompatible : un client plus ancien qui n'envoie pas d'identifiant
        // continue de fonctionner, sans la protection contre les doublons.
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.id").isNotEmpty());
    }

    /** @param id identifiant a envoyer, ou {@code null} pour l'omettre */
    private String runBody(String id) {
        String idLine = id == null ? "" : "\"id\": \"%s\",".formatted(id);
        return """
                {
                  %s
                  "sportType": "RUN",
                  "startedAt": "2026-08-10T06:00:00Z",
                  "endedAt": "2026-08-10T06:06:00Z",
                  "perceivedEffort": 6,
                  "gpsPoints": [
                    {"latitude": 48.8566, "longitude": 2.3522, "recordedAt": "2026-08-10T06:00:00Z"},
                    {"latitude": 48.8655931, "longitude": 2.3522, "recordedAt": "2026-08-10T06:06:00Z"}
                  ]
                }
                """.formatted(idLine);
    }
}
