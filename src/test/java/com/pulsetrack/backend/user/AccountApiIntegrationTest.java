package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce qu'un utilisateur peut faire de son propre compte : changer son mot de
 * passe, et partir avec ses donnees.
 *
 * <p>Les deux operations sont gardees par le mot de passe actuel. C'est le
 * controle le plus important de cette classe : un jeton valide ne doit pas
 * suffire, sinon un telephone laisse deverrouille donnerait le compte a qui le
 * ramasse.
 */
class AccountApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void change_le_mot_de_passe_et_rend_une_session_utilisable() throws Exception {
        String email = uniqueEmail();
        String token = bearerOf(register(email, "motdepasse123"));

        String reponse = changePassword(token, "motdepasse123", "nouveaumotdepasse456")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // La session rendue doit fonctionner : l'utilisateur ne doit pas etre
        // deconnecte pour avoir suivi la procedure.
        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", bearerOf(reponse)))
                .andExpect(status().isNotFound());

        login(email, "nouveaumotdepasse456").andExpect(status().isOk());
        login(email, "motdepasse123").andExpect(status().isUnauthorized());
    }

    @Test
    void revoque_les_autres_sessions() throws Exception {
        String email = uniqueEmail();
        String session = register(email, "motdepasse123");
        String ancienRefresh = json(session).get("refreshToken").asText();

        changePassword(bearerOf(session), "motdepasse123", "nouveaumotdepasse456")
                .andExpect(status().isOk());

        // Changer de mot de passe apres avoir prete son telephone n'aurait
        // aucun effet si la session ouverte sur ce telephone survivait.
        refresh(ancienRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_un_mot_de_passe_actuel_faux() throws Exception {
        String token = bearerOf(register(uniqueEmail(), "motdepasse123"));

        changePassword(token, "ce-n-est-pas-le-bon", "nouveaumotdepasse456")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Le mot de passe actuel est incorrect."));
    }

    @Test
    void refuse_un_nouveau_mot_de_passe_identique_a_l_ancien() throws Exception {
        String token = bearerOf(register(uniqueEmail(), "motdepasse123"));

        // Sinon l'utilisateur croirait avoir change quelque chose alors que
        // rien n'a bouge, et se croirait protege a tort.
        changePassword(token, "motdepasse123", "motdepasse123")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Le nouveau mot de passe doit differer de l'ancien."));
    }

    @Test
    void impose_les_memes_exigences_qu_a_l_inscription() throws Exception {
        String token = bearerOf(register(uniqueEmail(), "motdepasse123"));

        changePassword(token, "motdepasse123", "court")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
    }

    @Test
    void supprime_le_compte_et_tout_ce_qui_s_y_rattache() throws Exception {
        String email = uniqueEmail();
        String token = bearerOf(register(email, "motdepasse123"));
        saveProfile(token, 78.5);

        deleteAccount(token, "motdepasse123").andExpect(status().isNoContent());

        // Le profil est parti avec le compte, par cascade en base. Le jeton
        // d'acces reste signe et valide jusqu'a son expiration : c'est ce qu'il
        // trouve qui a disparu.
        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isNotFound());
        login(email, "motdepasse123").andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_la_suppression_sans_le_mot_de_passe_actuel() throws Exception {
        String email = uniqueEmail();
        String token = bearerOf(register(email, "motdepasse123"));

        deleteAccount(token, "ce-n-est-pas-le-bon")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Le mot de passe est incorrect."));

        // L'operation est irreversible : elle ne doit pas etre a portee d'un
        // jeton ramasse au vol.
        login(email, "motdepasse123").andExpect(status().isOk());
    }

    @Test
    void refuse_ces_deux_operations_sans_jeton() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "motdepasse123", "newPassword": "nouveaumotdepasse456"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "motdepasse123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // --- utilitaires -------------------------------------------------------

    private ResultActions changePassword(String authorization, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/me/password")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(current, next)));
    }

    private ResultActions deleteAccount(String authorization, String password) throws Exception {
        return mockMvc.perform(delete("/api/v1/me")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password": "%s"}
                        """.formatted(password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private String register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String bearerOf(String authResponse) throws Exception {
        return "Bearer " + json(authResponse).get("accessToken").asText();
    }

    private String uniqueEmail() {
        return "compte-" + UUID.randomUUID() + "@pulsetrack.test";
    }
}
