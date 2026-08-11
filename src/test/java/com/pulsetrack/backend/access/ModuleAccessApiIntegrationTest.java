package com.pulsetrack.backend.access;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouillage par module de bout en bout : l'intercepteur est-il reellement
 * branche, et le refus sort-il au format d'erreur de l'API.
 *
 * <p>Socle restreint declare ici, en surcharge de celui de la classe de base :
 * ces tests eprouvent le mecanisme, pas la composition du socle de production,
 * qui est une decision produit destinee a bouger. Il suffit qu'un module soit
 * dedans et un autre dehors.
 */
@TestPropertySource(properties = "pulsetrack.access.default-modules=WORKOUTS,GOALS")
class ModuleAccessApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private ModuleAccessService moduleAccess;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccessProperties accessProperties;

    /**
     * Une inscription n'ouvre que le socle. L'assertion se lit dans la
     * configuration plutot que d'etre codee en dur : la composition du socle est
     * un reglage produit, qui bougera sans que ce test doive etre reecrit.
     */
    @Test
    void une_inscription_ouvre_le_socle_et_lui_seul() throws Exception {
        String email = "socle-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = registerAndGetToken(email);

        Set<AppModule> granted = moduleAccess.enabledFor(idOf(email), false);

        assertThat(granted).isEqualTo(accessProperties.defaultModules());
        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(AppModule.values().length));
    }

    /**
     * Le pendant de la regle : ce qui n'est pas dans le socle est ferme des la
     * premiere seconde, et c'est un geste de l'administrateur qui l'ouvrira.
     */
    @Test
    void un_module_hors_socle_est_ferme_des_l_inscription() throws Exception {
        String email = "hors-socle-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = registerAndGetToken(email);

        AppModule horsSocle = Arrays.stream(AppModule.values())
                .filter(module -> !accessProperties.defaultModules().contains(module))
                .findFirst()
                .orElseThrow(() -> new AssertionError("le socle couvre tous les modules, ce test n'a plus d'objet"));

        assertThat(moduleAccess.isEnabled(idOf(email), false, horsSocle)).isFalse();

        mockMvc.perform(get("/api/v1/me/coach/settings").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/module-locked"))
                .andExpect(jsonPath("$.module").value("COACH"));
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
