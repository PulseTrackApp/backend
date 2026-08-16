package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat de l'inscription, de la connexion, du renouvellement de session et de
 * la fermeture par defaut.
 */
class AuthApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void inscrit_un_nouveau_compte_et_renvoie_un_jeton() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                // Le profil sportif reste a remplir : le mobile enchaine sur l'onboarding.
                .andExpect(jsonPath("$.profileCompleted").value(false));
    }

    @Test
    void refuse_une_inscription_avec_un_email_deja_pris() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "motdepasse123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Casse differente : l'unicite doit rester insensible a la casse.
                        .content(registerBody(email.toUpperCase(), "motdepasse123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflit"));
    }

    @Test
    void refuse_un_email_invalide_et_detaille_le_champ_fautif() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("pas-un-email", "motdepasse123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    @Test
    void refuse_un_mot_de_passe_trop_court() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "court")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void connecte_un_compte_existant() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "motdepasse123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "motdepasse123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refuse_un_mauvais_mot_de_passe_sans_dire_lequel_des_deux_est_faux() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "motdepasse123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "mauvaismotdepasse")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect."));
    }

    @Test
    void refuse_un_email_inconnu_avec_le_meme_message() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect."));
    }

    @Test
    void refuse_l_acces_a_une_ressource_protegee_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_un_jeton_invalide() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile")
                        .header("Authorization", "Bearer jeton.completement.invente"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laisse_passer_la_sonde_de_sante_sans_authentification() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Chemins ouverts : enumeres un par un, plus de joker `/api/v1/auth/**`
    // -----------------------------------------------------------------------

    @Test
    void exige_un_jeton_d_acces_pour_se_deconnecter() throws Exception {
        // C'est tout l'interet d'avoir supprime le joker : `/auth/logout` vit
        // sous le meme prefixe que la connexion, mais n'est pas public.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "peu-importe"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void n_ouvre_pas_les_chemins_inconnus_sous_le_prefixe_d_authentification() throws Exception {
        // Avec le joker, cette requete arrivait jusqu'au routage et repondait
        // 404 — ce qui revenait a annoncer que le chemin n'existe pas encore.
        mockMvc.perform(post("/api/v1/auth/change-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void n_ouvre_que_la_methode_POST_sur_les_chemins_d_authentification() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Jetons de renouvellement
    // -----------------------------------------------------------------------

    @Test
    void renvoie_un_jeton_de_renouvellement_a_l_inscription() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresInSeconds").value(30 * 24 * 3600));
    }

    @Test
    void renouvelle_une_session_et_fait_tourner_le_jeton() throws Exception {
        String refreshToken = registerAndReadRefreshToken();

        String renewed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Rotation : le jeton presente est consomme, un neuf le remplace.
        assertThat(json(renewed).get("refreshToken").asText()).isNotEqualTo(refreshToken);
    }

    @Test
    void refuse_un_jeton_de_renouvellement_inconnu() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("jeton-completement-invente")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Session expirée, veuillez vous reconnecter."));
    }

    @Test
    void revoque_toutes_les_sessions_au_rejeu_d_un_jeton_deja_consomme() throws Exception {
        String stolen = registerAndReadRefreshToken();

        String renewed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(stolen)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String current = json(renewed).get("refreshToken").asText();

        // Le jeton derobe est rejoue : on ne sait pas lequel des deux porteurs
        // est le bon, donc les deux perdent la main.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(stolen)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(current)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ferme_la_session_a_la_deconnexion() throws Exception {
        String response = register(uniqueEmail());
        String authorization = "Bearer " + json(response).get("accessToken").asText();
        String refreshToken = json(response).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accepte_une_deconnexion_rejouee_sans_broncher() throws Exception {
        String response = register(uniqueEmail());
        String authorization = "Bearer " + json(response).get("accessToken").asText();
        String refreshToken = json(response).get("refreshToken").asText();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(refreshToken)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void ne_laisse_pas_deconnecter_la_session_d_un_autre_compte() throws Exception {
        String victimRefreshToken = registerAndReadRefreshToken();
        String attacker = "Bearer " + json(register(uniqueEmail())).get("accessToken").asText();

        // Repond 204 sans rien faire : ne pas distinguer les deux cas evite de
        // confirmer a l'appelant qu'un jeton devine existe bel et bien.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(victimRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(victimRefreshToken)))
                .andExpect(status().isOk());
    }

    private String registerAndReadRefreshToken() throws Exception {
        return json(register(uniqueEmail())).get("refreshToken").asText();
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "motdepasse123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@pulsetrack.test";
    }

    private String registerBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
