package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification exigee : ce que devient le produit si Nicolas active le reglage.
 *
 * <p>La regle est celle-ci : l'inscription reste possible et rend une session,
 * mais toute session <em>ulterieure</em> — connexion comme renouvellement —
 * demande une adresse confirmee. Sans le controle au renouvellement, la session
 * de l'inscription se prolongerait de mois en mois et la verification ne serait
 * jamais exigee de personne.
 */
@TestPropertySource(properties = "pulsetrack.security.email-verification.required=true")
class EmailVerificationRequiredApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private VerificationCodeSender sender;

    @Test
    void laisse_s_inscrire_mais_refuse_la_connexion_suivante() throws Exception {
        String email = uniqueEmail();
        register(email);

        login(email)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://pulsetrack.app/problems/email-not-verified"));
    }

    @Test
    void laisse_entrer_une_fois_l_adresse_confirmee() throws Exception {
        String email = uniqueEmail();
        recorder().clear();
        register(email);

        verify(recorder().lastCode()).andExpect(status().isNoContent());

        login(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void refuse_de_renouveler_la_session_obtenue_a_l_inscription() throws Exception {
        String session = register(uniqueEmail());
        String refreshToken = json(session).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ne_revele_pas_l_etat_de_verification_a_qui_ignore_le_mot_de_passe() throws Exception {
        String email = uniqueEmail();
        register(email);

        // Un mauvais mot de passe donne 401, jamais le 403 qui trahirait
        // l'existence du compte.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "ce-n-est-pas-le-bon")))
                .andExpect(status().isUnauthorized());
    }

    // --- utilitaires -------------------------------------------------------

    private RecordingVerificationCodeSender recorder() {
        return (RecordingVerificationCodeSender) sender;
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "motdepasse123")));
    }

    private ResultActions verify(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}
                        """.formatted(code)));
    }

    private String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String uniqueEmail() {
        return "exige-" + UUID.randomUUID() + "@pulsetrack.test";
    }
}
