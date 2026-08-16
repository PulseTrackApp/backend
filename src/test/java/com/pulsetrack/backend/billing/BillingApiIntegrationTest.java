package com.pulsetrack.backend.billing;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarification et droit d'usage.
 *
 * <p>Aujourd'hui le paiement n'est pas exige : ces tests verifient surtout que
 * l'ecran de tarifs et l'ecran de paiement peuvent etre construits et eprouves
 * des maintenant, et que le jour ou le verrou s'active, ce qui doit rester
 * gratuit le reste.
 */
class BillingApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void expose_le_catalogue_marque_a_venir() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("MONTHLY"))
                // Le bouton de souscription doit rester inactif : rien n'est en vente.
                .andExpect(jsonPath("$[0].availability").value("COMING_SOON"))
                .andExpect(jsonPath("$[1].availability").value("COMING_SOON"))
                // Prix mis en forme cote serveur : Android et iOS affichent le
                // meme texte, et la devise n'est codee en dur nulle part.
                .andExpect(jsonPath("$[0].priceLabel").value("2000 FCFA / mois"))
                .andExpect(jsonPath("$[1].priceLabel").value("20000 FCFA / an"))
                .andExpect(jsonPath("$[1].highlighted").value(true))
                .andExpect(jsonPath("$[0].features").isNotEmpty());
    }

    @Test
    void place_un_compte_neuf_en_periode_d_essai() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/me/subscription").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.accessGranted").value(true))
                .andExpect(jsonPath("$.enforced").value(false))
                .andExpect(jsonPath("$.daysLeft").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void n_exige_rien_tant_que_le_paiement_dort() throws Exception {
        String token = registerUser();

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isOk());
    }

    /**
     * Essai de duree negative : tout compte est immediatement expire. C'est le
     * seul moyen d'eprouver le refus sans attendre trente jours.
     */
    @TestPropertySource(properties = {
            "pulsetrack.billing.enforced=true",
            "pulsetrack.billing.trial-days=-1",
            "pulsetrack.access.admin-email=" + PaiementExige.ADMIN_EMAIL
    })
    static class PaiementExige extends AbstractApiIntegrationTest {

        static final String ADMIN_EMAIL = "patron-billing@pulsetrack.test";

        @Test
        void refuse_les_routes_payantes_avec_de_quoi_afficher_un_prix() throws Exception {
            String token = registerUser();

            // Une route payante : l'historique des seances. `/me/modules` reste
            // gratuit, c'est le noyau.
            mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.type")
                            .value("https://pulsetrack.app/problems/subscription-required"))
                    .andExpect(jsonPath("$.subscriptionStatus").value("EXPIRED"))
                    // L'offre voyage avec le refus : relancer une requete pour
                    // afficher un prix, au moment ou tout est refuse, serait absurde.
                    .andExpect(jsonPath("$.suggestedPlan.code").value("YEARLY"))
                    .andExpect(jsonPath("$.suggestedPlan.priceLabel").isNotEmpty());
        }

        @Test
        void laisse_gratuits_les_tarifs_et_l_etat_de_l_abonnement() throws Exception {
            String token = registerUser();

            // Un ecran de paiement dont les prix se font refuser n'aurait aucun sens.
            mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/me/subscription").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EXPIRED"))
                    .andExpect(jsonPath("$.accessGranted").value(false))
                    .andExpect(jsonPath("$.enforced").value(true));
        }

        @Test
        void laisse_gratuits_le_profil_et_l_export_de_ses_donnees() throws Exception {
            String token = registerUser();
            saveProfile(token, 70.0);

            // Retenir les donnees de quelqu'un parce qu'il a cesse de payer
            // serait indefendable, et l'enfermer hors de son compte encore plus.
            mockMvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/me/export").header("Authorization", token))
                    .andExpect(status().isOk());
        }

        @Test
        void n_arrete_pas_un_administrateur() throws Exception {
            // Meme immunite que pour les modules : une erreur de configuration
            // ne doit pas enfermer dehors la seule personne capable de la corriger.
            String admin = registerAdmin();

            mockMvc.perform(get("/api/v1/workouts").header("Authorization", admin))
                    .andExpect(status().isOk());
        }

        /**
         * Inscrit le compte designe par {@code pulsetrack.access.admin-email} :
         * l'amorçage le promeut administrateur au moment de l'inscription.
         */
        private String registerAdmin() throws Exception {
            String body = """
                    {"email": "%s", "password": "motdepasse123"}
                    """.formatted(ADMIN_EMAIL);

            String response = mockMvc.perform(
                            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .post("/api/v1/auth/register")
                                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            return "Bearer " + json(response).get("accessToken").asText();
        }
    }
}
