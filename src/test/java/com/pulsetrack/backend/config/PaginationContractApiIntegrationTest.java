package com.pulsetrack.backend.config;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forme des reponses paginees, tenue pour un contrat.
 *
 * <p>Ces tests ne verifient pas une fonctionnalite mais une <em>garantie</em> :
 * que le JSON servi aux clients ne changera pas de forme au gre d'une montee de
 * version de Spring Data. Sans {@code WebConfig}, les controleurs serialisent
 * {@code PageImpl}, dont Spring ne garantit pas la structure — l'application
 * mobile cesserait d'afficher les listes sans qu'aucun autre test ne bronche.
 */
class PaginationContractApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void expose_les_metadonnees_de_page_sous_la_cle_page() throws Exception {
        String token = registerUser();
        createGoal(token, "WEEKLY_DISTANCE", 30);
        createGoal(token, "WEEKLY_SESSIONS", 3);

        mockMvc.perform(get("/api/v1/me/goals").param("size", "1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void n_expose_plus_la_forme_interne_de_PageImpl() throws Exception {
        String token = registerUser();
        createGoal(token, "WEEKLY_DISTANCE", 30);

        // C'est le vrai garde-fou : si quelqu'un retire
        // `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`, ces
        // champs reapparaissent a la racine et ce test tombe.
        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void sert_la_seconde_page_sans_repeter_la_premiere() throws Exception {
        String token = registerUser();
        createGoal(token, "WEEKLY_DISTANCE", 30);
        createGoal(token, "WEEKLY_SESSIONS", 3);

        mockMvc.perform(get("/api/v1/me/goals")
                        .param("size", "1")
                        .param("page", "1")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void applique_la_meme_forme_a_l_historique_des_seances() throws Exception {
        String token = registerUser();

        // Historique vide : la forme doit rester la meme, sans quoi le client
        // aurait deux cas a traiter selon qu'il y a des donnees ou non.
        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.totalElements").doesNotExist());
    }

    @Test
    void applique_la_meme_forme_aux_pesees() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/body-checkins").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    private void createGoal(String token, String type, double target) throws Exception {
        mockMvc.perform(post("/api/v1/me/goals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "%s", "targetValue": %s}
                                """.formatted(type, target)))
                .andExpect(status().isCreated());
    }
}
