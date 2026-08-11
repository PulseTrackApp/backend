package com.pulsetrack.backend.access;

import java.util.EnumSet;
import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouillage par module de bout en bout : l'intercepteur est-il reellement
 * branche, et le refus sort-il au format d'erreur de l'API.
 */
class ModuleAccessApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private ModuleAccessService moduleAccess;

    @Autowired
    private UserRepository users;

    @Test
    void une_inscription_ouvre_tous_les_modules() throws Exception {
        String token = registerAndGetToken("tous-" + UUID.randomUUID() + "@pulsetrack.test");

        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(AppModule.values().length))
                .andExpect(jsonPath("$.modules[?(@.enabled == false)]").isEmpty());
    }

    @Test
    void un_module_retire_ferme_ses_routes_et_nomme_le_module() throws Exception {
        String email = "restreint-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = registerAndGetToken(email);
        moduleAccess.replace(idOf(email), EnumSet.of(AppModule.STATS));

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/module-locked"))
                .andExpect(jsonPath("$.module").value("GOALS"));
    }

    @Test
    void un_module_conserve_reste_accessible() throws Exception {
        String email = "partiel-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = registerAndGetToken(email);
        moduleAccess.replace(idOf(email), EnumSet.of(AppModule.GOALS));

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /**
     * Le geste le plus radical de l'ecran d'administration ne doit pas enfermer
     * l'utilisateur hors de son compte : profil, authentification et lecture de
     * ses propres droits restent ouverts.
     */
    @Test
    void tout_retirer_laisse_le_noyau_ouvert() throws Exception {
        String email = "muet-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = registerAndGetToken(email);
        moduleAccess.replace(idOf(email), EnumSet.noneOf(AppModule.class));

        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.enabled == true)]").isEmpty());
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(email);
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }

    private UUID idOf(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new AssertionError("compte introuvable : " + email))
                .getId();
    }

    @Test
    void le_compte_cree_porte_le_role_utilisateur() throws Exception {
        String email = "role-" + UUID.randomUUID() + "@pulsetrack.test";
        registerAndGetToken(email);

        assertThat(users.findByEmail(email).orElseThrow().isAdmin()).isFalse();
    }
}
