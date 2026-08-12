package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmation de l'adresse email, verification non exigee.
 *
 * <p>C'est la configuration de production a ce jour : le compte fonctionne sans
 * confirmation, mais l'etat est connu du client et le parcours est disponible.
 * L'exigence est eprouvee a part, dans
 * {@code EmailVerificationRequiredApiIntegrationTest}.
 */
class EmailVerificationApiIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private VerificationCodeSender sender;

    @Test
    void emet_un_code_a_l_inscription_et_marque_l_adresse_apres_confirmation() throws Exception {
        String email = uniqueEmail();
        recorder().clear();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));

        assertThat(recorder().lastEmail()).isEqualTo(email);
        verify(recorder().lastCode()).andExpect(status().isNoContent());

        login(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void refuse_un_code_invente() throws Exception {
        verify("ABCD2345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ce code de verification est invalide ou a expire."));
    }

    @Test
    void refuse_un_code_deja_utilise() throws Exception {
        String code = registerAndReadCode(uniqueEmail());

        verify(code).andExpect(status().isNoContent());
        // Rejouer le code ne doit rien donner : la ligne survit a son usage
        // precisement pour permettre ce refus.
        verify(code).andExpect(status().isBadRequest());
    }

    @Test
    void accepte_un_code_recopie_en_minuscules_ou_avec_des_espaces() throws Exception {
        String code = registerAndReadCode(uniqueEmail());

        String maladroit = " " + code.toLowerCase().substring(0, 4) + " " + code.substring(4) + " ";
        verify(maladroit).andExpect(status().isNoContent());
    }

    @Test
    void invalide_le_code_precedent_quand_on_en_redemande_un() throws Exception {
        String email = uniqueEmail();
        String premier = registerAndReadCode(email);

        recorder().clear();
        resend(email).andExpect(status().isNoContent());
        String second = recorder().lastCode();
        assertThat(second).isNotEqualTo(premier);

        verify(premier).andExpect(status().isBadRequest());
        verify(second).andExpect(status().isNoContent());
    }

    @Test
    void repond_pareil_pour_une_adresse_inconnue_et_n_emet_aucun_code() throws Exception {
        recorder().clear();

        // Distinguer les cas ferait de cet endpoint un moyen commode de savoir
        // qui possede un compte.
        resend(uniqueEmail()).andExpect(status().isNoContent());

        assertThat(recorder().lastCodeOrNull()).isNull();
    }

    @Test
    void n_emet_aucun_code_pour_un_compte_deja_verifie() throws Exception {
        String email = uniqueEmail();
        verify(registerAndReadCode(email)).andExpect(status().isNoContent());

        recorder().clear();
        resend(email).andExpect(status().isNoContent());

        // Meme reponse que pour une adresse inconnue, et rien n'est envoye :
        // inutile d'inonder une boite pour une formalite deja accomplie.
        assertThat(recorder().lastCodeOrNull()).isNull();
    }

    // --- utilitaires -------------------------------------------------------

    private RecordingVerificationCodeSender recorder() {
        return (RecordingVerificationCodeSender) sender;
    }

    private String registerAndReadCode(String email) throws Exception {
        recorder().clear();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isCreated());
        return recorder().lastCode();
    }

    private ResultActions verify(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}
                        """.formatted(code)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, "motdepasse123")));
    }

    private String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String uniqueEmail() {
        return "verif-" + UUID.randomUUID() + "@pulsetrack.test";
    }
}
