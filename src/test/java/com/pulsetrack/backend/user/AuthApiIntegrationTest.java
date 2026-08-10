package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat de l'inscription, de la connexion et de la fermeture par defaut.
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

    private String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@pulsetrack.test";
    }

    private String registerBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
