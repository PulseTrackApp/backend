package com.pulsetrack.backend.admin;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;
import com.pulsetrack.backend.access.AccessProperties;
import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Espace d'administration : fermeture aux non-administrateurs, pilotage des
 * droits, et garde-fous contre l'auto-sabotage.
 */
@TestPropertySource(properties = "pulsetrack.access.admin-email=" + AdminApiIntegrationTest.ADMIN_EMAIL)
class AdminApiIntegrationTest extends AbstractApiIntegrationTest {

    static final String ADMIN_EMAIL = "chef@pulsetrack.test";

    @Autowired
    private UserRepository users;

    @Autowired
    private AccessProperties accessProperties;

    /**
     * Le controle le plus important de cette classe : sans lui, n'importe quel
     * utilisateur inscrit lirait la liste de tous les comptes.
     */
    @Test
    void ferme_l_administration_a_un_utilisateur_ordinaire() throws Exception {
        String token = accountOf("intrus-" + UUID.randomUUID() + "@pulsetrack.test");

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse_l_administration_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publie_le_catalogue_des_modules() throws Exception {
        mockMvc.perform(get("/api/v1/admin/modules").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(AppModule.values().length))
                .andExpect(jsonPath("$[0].label").isNotEmpty());
    }

    @Test
    void retrouve_un_compte_par_fragment_d_adresse() throws Exception {
        String fragment = "pepite" + UUID.randomUUID().toString().substring(0, 8);
        accountOf(fragment + "@pulsetrack.test");

        mockMvc.perform(get("/api/v1/admin/users").param("q", fragment).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].enabledModules.length()")
                        .value(accessProperties.defaultModules().size()));
    }

    /**
     * Le parcours complet du produit : l'administrateur ferme un module, et
     * l'utilisateur vise le constate immediatement. C'est la promesse « moins
     * d'une minute » du contrat, ici sans aucun delai puisque le cache est vide
     * a l'ecriture.
     */
    @Test
    void fermer_un_module_coupe_aussitot_la_route_de_l_utilisateur() throws Exception {
        String email = "cible-" + UUID.randomUUID() + "@pulsetrack.test";
        String userToken = accountOf(email);
        UUID id = users.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", userToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/users/{id}/modules", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modules": ["STATS"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules.length()").value(1));

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.module").value("GOALS"));
    }

    @Test
    void refuse_un_module_inconnu() throws Exception {
        UUID id = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/admin/users/{id}/modules", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modules": ["TELEPORTATION"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repond_introuvable_pour_un_compte_inexistant() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID()).header("Authorization", admin()))
                .andExpect(status().isNotFound());
    }

    /**
     * Sans ce garde-fou, le dernier administrateur pourrait se dechoir et plus
     * personne n'administrerait rien — panne irreparable depuis l'application.
     */
    @Test
    void empeche_un_administrateur_de_se_dechoir() throws Exception {
        UUID id = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/admin/users/{id}/role", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "USER"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void empeche_un_administrateur_de_se_supprimer() throws Exception {
        UUID id = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", id).header("Authorization", admin()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void supprime_un_compte_et_ses_donnees() throws Exception {
        String email = "adieu-" + UUID.randomUUID() + "@pulsetrack.test";
        accountOf(email);
        UUID id = users.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", id).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void promeut_un_utilisateur_en_administrateur() throws Exception {
        String email = "futur-chef-" + UUID.randomUUID() + "@pulsetrack.test";
        accountOf(email);
        UUID id = users.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/admin/users/{id}/role", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void publie_tous_les_modules_dans_la_repartition_d_usage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleUsage.length()").value(AppModule.values().length))
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.admins").isNumber());
    }

    private String admin() throws Exception {
        return accountOf(ADMIN_EMAIL);
    }

    /**
     * S'inscrit, ou se connecte si le compte existe deja : la base n'est pas
     * remise a zero entre deux methodes, et l'adresse de l'administrateur est
     * fixe par nature. Sans cela, l'ordre d'execution deciderait du resultat.
     */
    private String accountOf(String email) throws Exception {
        String path = users.findByEmail(email).isPresent() ? "/api/v1/auth/login" : "/api/v1/auth/register";
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }

    private static String credentials(String email) {
        return """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(email);
    }
}
