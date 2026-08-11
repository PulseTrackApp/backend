package com.pulsetrack.backend.access;

import java.util.EnumSet;

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
 * Promotion du compte administrateur declare, et immunite qui en decoule.
 *
 * <p>Le cas qui compte est celui du premier demarrage : l'adresse est declaree
 * avant que le compte existe. Sans promotion a l'inscription, le tout premier
 * administrateur devrait redemarrer le serveur pour obtenir ses droits.
 */
@TestPropertySource(properties = "pulsetrack.access.admin-email=" + AdminPromotionApiIntegrationTest.ADMIN_EMAIL)
class AdminPromotionApiIntegrationTest extends AbstractApiIntegrationTest {

    static final String ADMIN_EMAIL = "patron@pulsetrack.test";

    @Autowired
    private ModuleAccessService moduleAccess;

    @Autowired
    private UserRepository users;

    @Test
    void promeut_a_l_inscription_le_compte_declare_administrateur() throws Exception {
        register(ADMIN_EMAIL);

        assertThat(users.findByEmail(ADMIN_EMAIL).orElseThrow().isAdmin()).isTrue();
    }

    /**
     * L'application desktop d'administration lit ce champ pour savoir, des la
     * connexion, si elle a affaire a un administrateur. Sans lui, elle devrait
     * decoder le jeton elle-meme, ou laisser entrer un compte ordinaire vers des
     * ecrans qui repondront tous {@code 403}.
     */
    @Test
    void annonce_le_role_des_la_connexion() throws Exception {
        register(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "motdepasse123"}
                                """.formatted(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void annonce_un_role_ordinaire_pour_les_autres() throws Exception {
        register("sans-privilege@pulsetrack.test");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "sans-privilege@pulsetrack.test", "password": "motdepasse123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void n_effleure_pas_les_autres_comptes() throws Exception {
        register("simple@pulsetrack.test");

        assertThat(users.findByEmail("simple@pulsetrack.test").orElseThrow().isAdmin()).isFalse();
    }

    /**
     * Un administrateur prive d'un module ne pourrait plus le rouvrir a
     * personne, lui compris. Le verrouillage ne doit donc pas mordre sur lui,
     * meme quand la base ne lui accorde plus rien.
     */
    @Test
    void un_administrateur_reste_ouvert_meme_prive_de_tous_ses_modules() throws Exception {
        String token = register(ADMIN_EMAIL);
        moduleAccess.replace(users.findByEmail(ADMIN_EMAIL).orElseThrow().getId(),
                EnumSet.noneOf(AppModule.class));

        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[?(@.enabled == false)]").isEmpty());

        mockMvc.perform(get("/api/v1/me/goals").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /**
     * S'inscrit, ou se connecte si le compte existe deja.
     *
     * <p>Les adresses sont ici fixes — c'est l'adresse declaree administrateur
     * que l'on eprouve — et la base n'est pas remise a zero entre deux methodes
     * de test. Une seconde inscription repondrait donc {@code 409}, et l'ordre
     * d'execution deciderait du resultat de la suite.
     */
    private String register(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(email);
        String path = users.findByEmail(email).isPresent() ? "/api/v1/auth/login" : "/api/v1/auth/register";
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }
}
