package com.pulsetrack.backend.coach;

import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat de l'assistant Gemini.
 *
 * <p>{@code GeminiClient} est remplace par un mock : on valide notre logique
 * (chiffrement, cache, garde-fous d'usage) sans appeler l'API de Google, ce qui
 * rendrait les tests lents, payants et dependants du reseau.
 */
class CoachApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String FAKE_KEY = "AIzaSyFAKE-KEY-FOR-TESTS-0123456789";

    @MockitoBean
    private GeminiClient geminiClient;

    @Autowired
    private GeminiSettingsRepository geminiSettings;

    @Autowired
    private TextEncryptor apiKeyEncryptor;

    // ----- Reglages et cle API ----------------------------------------------

    @Test
    void l_assistant_est_desactive_par_defaut() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/coach/settings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.apiKeyStored").value(false))
                .andExpect(jsonPath("$.usable").value(false))
                .andExpect(jsonPath("$.coachingTone").value("ENCOURAGING"));
    }

    @Test
    void enregistre_la_cle_sans_jamais_la_renvoyer() throws Exception {
        String token = registerUser();

        String response = storeKey(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyStored").value(true))
                .andExpect(jsonPath("$.usable").value(true))
                .andReturn().getResponse().getContentAsString();

        // Aucun champ de la reponse ne doit contenir la cle, meme tronquee.
        assertThat(response).doesNotContain(FAKE_KEY);
        assertThat(response).doesNotContain(FAKE_KEY.substring(0, 12));
        // `apiKeyStored` est legitime ; un champ `apiKey` ne le serait pas.
        assertThat(response).doesNotContain("\"apiKey\"");
    }

    @Test
    void stocke_la_cle_chiffree_et_non_en_clair() throws Exception {
        String token = registerUser();
        storeKey(token).andExpect(status().isOk());

        UUID userId = userIdOf(token);
        GeminiSettings stored = geminiSettings.findById(userId).orElseThrow();

        // Une copie de la base, seule, ne doit livrer aucune cle exploitable.
        assertThat(stored.getEncryptedApiKey()).isNotNull().isNotEqualTo(FAKE_KEY);
        assertThat(apiKeyEncryptor.decrypt(stored.getEncryptedApiKey())).isEqualTo(FAKE_KEY);
    }

    @Test
    void supprimer_la_cle_desactive_l_assistant() throws Exception {
        String token = registerUser();
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/me/coach/settings/api-key").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyStored").value(false))
                // Actif sans cle ne produirait que des erreurs a chaque appel.
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.usable").value(false));
    }

    @Test
    void refuse_une_cle_trop_courte() throws Exception {
        String token = registerUser();

        mockMvc.perform(put("/api/v1/me/coach/settings/api-key")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey": "trop-court"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.apiKey").isNotEmpty());
    }

    @Test
    void change_le_ton_du_coach() throws Exception {
        String token = registerUser();
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/me/coach/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "coachingTone": "DEMANDING",
                                 "weeklyReviewEnabled": true, "effortWarningsEnabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachingTone").value("DEMANDING"))
                .andExpect(jsonPath("$.effortWarningsEnabled").value(false))
                // Changer le ton ne doit pas faire perdre la cle.
                .andExpect(jsonPath("$.apiKeyStored").value(true));
    }

    // ----- Conseils ----------------------------------------------------------

    @Test
    void refuse_de_conseiller_tant_que_la_cle_n_est_pas_configuree() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.0);

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "L'assistant n'est pas configure. Ajoutez votre cle API dans les parametres."));

        // Et surtout : aucun appel n'a ete tente.
        verify(geminiClient, never()).generate(anyString(), anyString(), anyString());
    }

    /**
     * Regle produit : rien de ce qui sort du serveur ne doit reveler quel
     * fournisseur alimente l'assistant. Ce test verrouille la regle sur le
     * chemin d'erreur le plus expose — celui qu'un utilisateur declenche en
     * essayant la fonctionnalite sans l'avoir configuree.
     *
     * <p>Le nom reste dans les journaux du serveur, ou il est utile a
     * l'exploitation et invisible aux clients.
     */
    @Test
    void ne_nomme_jamais_le_fournisseur_dans_une_reponse() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.0);

        String body = mockMvc.perform(post("/api/v1/me/coach/ask")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Comment progresser en endurance ?"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("gemini");
    }

    @Test
    void produit_un_bilan_hebdomadaire_puis_le_relit_sans_rappeler_gemini() throws Exception {
        given(geminiClient.generate(eq(FAKE_KEY), anyString(), anyString()))
                .willReturn("Deux seances sur quatre. Une marche de 35 minutes demain te remettrait dans les temps.");

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WEEKLY_REVIEW"))
                .andExpect(jsonPath("$.content").value(
                        "Deux seances sur quatre. Une marche de 35 minutes demain te remettrait dans les temps."))
                .andExpect(jsonPath("$.fromCache").value(false));

        // Deuxieme ouverture du dashboard le meme jour : le quota de
        // l'utilisateur ne doit pas etre consomme une seconde fois.
        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(true));

        verify(geminiClient, times(1)).generate(anyString(), anyString(), anyString());
    }

    @Test
    void regenere_le_bilan_sur_demande_explicite() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString()))
                .willReturn("Premier avis", "Second avis");

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Premier avis"));

        mockMvc.perform(post("/api/v1/me/coach/weekly-review")
                        .param("refresh", "true")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Second avis"))
                .andExpect(jsonPath("$.fromCache").value(false));

        verify(geminiClient, times(2)).generate(anyString(), anyString(), anyString());
    }

    @Test
    void repond_a_une_question_libre_sans_la_mettre_en_cache() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString()))
                .willReturn("Garde deux sorties faciles avant d'ajouter de l'intensite.");

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/me/coach/ask")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"question": "Puis-je courir demain ?"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("FREE_QUESTION"))
                    .andExpect(jsonPath("$.fromCache").value(false));
        }

        // Deux questions, deux appels : la reponse depend du moment ou on la pose.
        verify(geminiClient, times(2)).generate(anyString(), anyString(), anyString());
    }

    @Test
    void transmet_le_ton_choisi_au_modele() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString())).willReturn("Avis");

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/coach/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "coachingTone": "FACTUAL",
                                 "weeklyReviewEnabled": true, "effortWarningsEnabled": true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> systemInstruction =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generate(eq(FAKE_KEY), systemInstruction.capture(), anyString());
        assertThat(systemInstruction.getValue()).contains("factuel et direct");
    }

    @Test
    void refuse_une_question_vide() throws Exception {
        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/ask")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "  "}
                                """))
                .andExpect(status().isBadRequest());

        verify(geminiClient, never()).generate(anyString(), anyString(), anyString());
    }

    @Test
    void le_dernier_conseil_reste_lisible_sans_appeler_gemini() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString())).willReturn("Bon rythme.");

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isOk());

        // Meme apres suppression de la cle, l'historique reste consultable.
        mockMvc.perform(delete("/api/v1/me/coach/settings/api-key").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/coach/latest").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Bon rythme."))
                .andExpect(jsonPath("$.fromCache").value(true));
    }

    @Test
    void repond_204_quand_aucun_conseil_n_existe() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/coach/latest").header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void ne_montre_pas_le_conseil_d_un_autre_compte() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString())).willReturn("Conseil d'Alice");

        String alice = registerUser();
        saveProfile(alice, 72.0);
        storeKey(alice).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", alice))
                .andExpect(status().isOk());

        String bob = registerUser();
        mockMvc.perform(get("/api/v1/me/coach/latest").header("Authorization", bob))
                .andExpect(status().isNoContent());
    }

    @Test
    void traduit_une_cle_refusee_en_erreur_actionnable() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString()))
                .willThrow(new com.pulsetrack.backend.common.error.BusinessRuleException(
                        "Votre cle API Gemini a ete refusee. Verifiez-la dans les parametres."));

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Votre cle API Gemini a ete refusee. Verifiez-la dans les parametres."));
    }

    @Test
    void traduit_un_quota_epuise_en_429() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString()))
                .willThrow(new com.pulsetrack.backend.common.error.RateLimitedException(
                        "Le quota de votre cle Gemini est atteint. Reessayez plus tard."));

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Quota atteint"));
    }

    @Test
    void traduit_une_panne_de_gemini_en_502() throws Exception {
        given(geminiClient.generate(anyString(), anyString(), anyString()))
                .willThrow(new com.pulsetrack.backend.common.error.ExternalServiceException(
                        "Gemini n'a pas repondu dans le delai imparti."));

        String token = registerUser();
        saveProfile(token, 72.0);
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Service indisponible"));
    }

    @Test
    void exige_un_profil_avant_de_conseiller() throws Exception {
        // Sans profil, le conseil se resumerait a des generalites.
        String token = registerUser();
        storeKey(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/coach/weekly-review").header("Authorization", token))
                .andExpect(status().isNotFound());

        verify(geminiClient, never()).generate(anyString(), anyString(), anyString());
    }

    // ----- Utilitaires -------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions storeKey(String authorization) throws Exception {
        return mockMvc.perform(put("/api/v1/me/coach/settings/api-key")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"apiKey": "%s"}
                        """.formatted(FAKE_KEY)));
    }

    /** Recupere l'identifiant porte par le jeton, pour lire directement en base. */
    private UUID userIdOf(String authorization) throws Exception {
        String response = mockMvc.perform(get("/api/v1/me/coach/settings").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).isNotBlank();

        // Le sujet du JWT est l'identifiant du compte.
        String payload = authorization.substring("Bearer ".length()).split("\\.")[1];
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
        return UUID.fromString(json(decoded).get("sub").asText());
    }
}
