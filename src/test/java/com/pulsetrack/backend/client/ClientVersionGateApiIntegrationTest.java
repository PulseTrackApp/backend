package com.pulsetrack.backend.client;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le verrou des applications trop anciennes, celui qui empechera de contourner
 * le paiement en gardant un vieil APK.
 */
class ClientVersionGateApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void annonce_ses_exigences_sans_authentification() throws Exception {
        // Route ouverte par necessite : une application perimee doit pouvoir
        // apprendre qu'elle l'est, et un utilisateur deconnecte comprendre
        // pourquoi sa connexion echoue.
        mockMvc.perform(get("/api/v1/client/requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumVersion").isNotEmpty())
                .andExpect(jsonPath("$.enforced").value(false))
                .andExpect(jsonPath("$.versionHeader").value("X-GymFlow-Client-Version"))
                .andExpect(jsonPath("$.platformHeader").value("X-GymFlow-Platform"));
    }

    @Test
    void laisse_tout_passer_tant_que_le_verrou_dort() throws Exception {
        String token = registerUser();

        // Aucune version annoncee, comme les APK deja distribues : accepte.
        mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /**
     * Verrou actif, minimum a 2.0.0. C'est l'etat vise le jour de la mise en
     * vente.
     */
    @TestPropertySource(properties = {
            "pulsetrack.client.enforced=true",
            "pulsetrack.client.minimum-version=2.0.0",
            "pulsetrack.client.android-store-url=https://play.google.com/store/apps/details?id=gymflow"
    })
    static class VerrouActif extends AbstractApiIntegrationTest {

        @Test
        void refuse_une_application_qui_n_annonce_aucune_version() throws Exception {
            String token = registerUpToDate();

            // C'est tout le mecanisme : les APK publies avant ce dispositif
            // n'envoient pas l'en-tete, et sont donc reconnus sans avoir rien a
            // retro-porter.
            mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                    .andExpect(status().isUpgradeRequired())
                    .andExpect(jsonPath("$.type")
                            .value("https://pulsetrack.app/problems/client-upgrade-required"))
                    .andExpect(jsonPath("$.minimumVersion").value("2.0.0"))
                    .andExpect(jsonPath("$.storeUrl").isNotEmpty());
        }

        @Test
        void refuse_une_version_trop_ancienne() throws Exception {
            String token = registerUpToDate();

            mockMvc.perform(get("/api/v1/me/modules")
                            .header("Authorization", token)
                            .header("X-GymFlow-Client-Version", "1.9.9")
                            .header("X-GymFlow-Platform", "ANDROID"))
                    .andExpect(status().isUpgradeRequired())
                    .andExpect(jsonPath("$.currentVersion").value("1.9.9"));
        }

        @Test
        void accepte_la_version_minimale_et_au_dela() throws Exception {
            String token = registerUpToDate();

            mockMvc.perform(get("/api/v1/me/modules")
                            .header("Authorization", token)
                            .header("X-GymFlow-Client-Version", "2.0.0"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/me/modules")
                            .header("Authorization", token)
                            .header("X-GymFlow-Client-Version", "2.10.0"))
                    .andExpect(status().isOk());
        }

        /**
         * L'inscription elle-meme est verrouillee, et c'est voulu : une
         * application perimee ne doit meme pas pouvoir creer un compte. Le
         * scenario est donc joue depuis un client a jour.
         */
        private String registerUpToDate() throws Exception {
            String body = """
                    {"email": "coureur-%s@pulsetrack.test", "password": "motdepasse123"}
                    """.formatted(java.util.UUID.randomUUID());

            String response = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post("/api/v1/auth/register")
                                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                    .header("X-GymFlow-Client-Version", "2.0.0")
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return "Bearer " + json(response).get("accessToken").asText();
        }

        @Test
        void refuse_meme_l_inscription_a_une_application_perimee() throws Exception {
            // C'est ce qui rend le verrou etanche : un vieil APK ne peut pas
            // contourner le paiement en creant un compte neuf.
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/auth/register")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "vieux@pulsetrack.test", "password": "motdepasse123"}
                                    """))
                    .andExpect(status().isUpgradeRequired());
        }

        @Test
        void laisse_passer_la_route_qui_annonce_les_exigences() throws Exception {
            // La fermer rendrait le dispositif inutilisable.
            mockMvc.perform(get("/api/v1/client/requirements"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enforced").value(true));
        }

        @Test
        void n_arrete_pas_une_plateforme_hors_du_champ_du_verrou() throws Exception {
            String token = registerUpToDate();

            // L'application desktop d'administration est distribuee a la main et
            // suit son propre rythme.
            mockMvc.perform(get("/api/v1/me/modules")
                            .header("Authorization", token)
                            .header("X-GymFlow-Platform", "DESKTOP"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Verrou actif mais sans minimum reel : il n'a rien a refuser. Garde-fou
     * contre une configuration a moitie posee en production.
     */
    @TestPropertySource(properties = "pulsetrack.client.enforced=true")
    static class VerrouSansMinimum extends AbstractApiIntegrationTest {

        @Test
        void ne_refuse_rien_quand_aucun_minimum_n_est_pose() throws Exception {
            String token = registerUser();

            mockMvc.perform(get("/api/v1/me/modules").header("Authorization", token))
                    .andExpect(status().isOk());
        }
    }
}
