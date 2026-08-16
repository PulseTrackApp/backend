package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet de reinitialisation de mot de passe.
 *
 * <p>Le code est recupere via l'expediteur de repli, celui qui journalise au
 * lieu d'envoyer : c'est exactement ce qui tourne en test, faute de SMTP.
 */
class PasswordResetApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private ResetCodeSender sender;

    @Test
    void permet_de_choisir_un_nouveau_mot_de_passe_et_de_se_connecter_avec() throws Exception {
        String email = uniqueEmail();
        register(email, "motdepasse123");

        String code = requestCodeFor(email);
        resetTo(code, "nouveaumotdepasse456").andExpect(status().isNoContent());

        login(email, "nouveaumotdepasse456").andExpect(status().isOk());
        // L'ancien ne vaut plus rien : c'est tout l'objet de l'operation.
        login(email, "motdepasse123").andExpect(status().isUnauthorized());
    }

    @Test
    void repond_pareil_pour_une_adresse_inconnue() throws Exception {
        // Distinguer les deux cas ferait de cet endpoint un moyen commode de
        // savoir qui possede un compte.
        forgot(uniqueEmail()).andExpect(status().isNoContent());
    }

    @Test
    void refuse_un_code_invente() throws Exception {
        register(uniqueEmail(), "motdepasse123");

        resetTo("ABCD2345", "nouveaumotdepasse456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Ce code de réinitialisation est invalide ou a expiré."));
    }

    @Test
    void refuse_un_code_deja_utilise() throws Exception {
        String email = uniqueEmail();
        register(email, "motdepasse123");
        String code = requestCodeFor(email);

        resetTo(code, "nouveaumotdepasse456").andExpect(status().isNoContent());

        // Rejouer le meme code ne doit rien donner, sinon quiconque a lu le
        // courriel garde un droit permanent sur le compte.
        resetTo(code, "encoreunautre789").andExpect(status().isBadRequest());
        login(email, "nouveaumotdepasse456").andExpect(status().isOk());
    }

    @Test
    void invalide_le_code_precedent_quand_on_en_redemande_un() throws Exception {
        String email = uniqueEmail();
        register(email, "motdepasse123");

        String premier = requestCodeFor(email);
        String second = requestCodeFor(email);
        assertThat(second).isNotEqualTo(premier);

        // Deux codes valides en meme temps doubleraient la surface d'attaque
        // sans rendre service a personne.
        resetTo(premier, "nouveaumotdepasse456").andExpect(status().isBadRequest());
        resetTo(second, "nouveaumotdepasse456").andExpect(status().isNoContent());
    }

    @Test
    void revoque_les_sessions_ouvertes() throws Exception {
        String email = uniqueEmail();
        String session = register(email, "motdepasse123");
        String accessToken = json(session).get("accessToken").asText();
        String refreshToken = json(session).get("refreshToken").asText();

        // La session fonctionne avant.
        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        resetTo(requestCodeFor(email), "nouveaumotdepasse456").andExpect(status().isNoContent());

        // Qui reinitialise soupconne souvent une intrusion : laisser vivre les
        // sessions de l'intrus viderait le geste de son sens.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accepte_un_code_recopie_en_minuscules_ou_avec_des_espaces() throws Exception {
        String email = uniqueEmail();
        register(email, "motdepasse123");
        String code = requestCodeFor(email);

        // Le code est lu dans un courriel puis recopie a la main : refuser une
        // espace ou une minuscule serait gratuitement penible.
        String maladroit = " " + code.toLowerCase().substring(0, 4) + " " + code.substring(4) + " ";
        resetTo(maladroit, "nouveaumotdepasse456").andExpect(status().isNoContent());
    }

    @Test
    void impose_les_memes_exigences_qu_a_l_inscription() throws Exception {
        String email = uniqueEmail();
        register(email, "motdepasse123");
        String code = requestCodeFor(email);

        // La reinitialisation ne doit pas devenir une porte derobee vers un mot
        // de passe plus faible que ceux acceptes a la creation du compte.
        resetTo(code, "court")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
    }

    // --- utilitaires -------------------------------------------------------

    /** Recupere le code via l'expediteur de repli, qui le retient au lieu de l'envoyer. */
    private String requestCodeFor(String email) throws Exception {
        ((RecordingResetCodeSender) sender).clear();
        forgot(email).andExpect(status().isNoContent());
        return ((RecordingResetCodeSender) sender).lastCode();
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions resetTo(String code, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "newPassword": "%s"}
                        """.formatted(code, password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
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

    private String uniqueEmail() {
        return "reset-" + UUID.randomUUID() + "@pulsetrack.test";
    }
}
