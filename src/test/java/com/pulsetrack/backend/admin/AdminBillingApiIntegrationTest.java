package com.pulsetrack.backend.admin;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;
import com.pulsetrack.backend.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestion du catalogue de tarifs depuis l'administration.
 *
 * <p>Le catalogue vivait en configuration ; il vit desormais en base pour qu'un
 * prix se corrige sans redeploiement. Ces tests tiennent les trois promesses qui
 * font la difference entre « editable » et « editable sans degat » : l'amorçage
 * depuis la configuration, l'unicite de l'offre mise en avant, et le refus de
 * supprimer une offre encore souscrite.
 */
@TestPropertySource(properties =
        "pulsetrack.access.admin-email=" + AdminBillingApiIntegrationTest.ADMIN_EMAIL)
class AdminBillingApiIntegrationTest extends AbstractApiIntegrationTest {

    static final String ADMIN_EMAIL = "tarifs@pulsetrack.test";

    @Autowired
    private UserRepository users;

    @Test
    void ferme_la_gestion_des_tarifs_a_un_utilisateur_ordinaire() throws Exception {
        String token = accountOf("curieux-" + UUID.randomUUID() + "@pulsetrack.test");

        mockMvc.perform(get("/api/v1/admin/billing/plans").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    /**
     * Une base neuve ne doit pas afficher un ecran de tarifs vide : la
     * configuration sert d'amorce au premier demarrage.
     */
    @Test
    void amorce_le_catalogue_depuis_la_configuration() throws Exception {
        // On cite les offres par leur code plutot que par leur rang : les autres
        // methodes de cette classe en creent et la base n'est pas remise a zero
        // entre elles, si bien qu'un test compteur dependrait de l'ordre
        // d'execution — et echouerait un jour sans que rien n'ait change.
        mockMvc.perform(get("/api/v1/admin/billing/plans").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'MONTHLY')].priceLabel")
                        .value("2000 FCFA / mois"))
                .andExpect(jsonPath("$[?(@.code == 'YEARLY')].priceLabel")
                        .value("20000 FCFA / an"))
                .andExpect(jsonPath("$[?(@.code == 'MONTHLY')].subscriberCount").value(0));
    }

    /**
     * Ce que l'ecran mobile lira apres correction : la modification doit
     * traverser jusqu'au catalogue public, sinon elle n'a servi a rien.
     */
    @Test
    void corrige_un_prix_et_le_catalogue_public_suit() throws Exception {
        String code = "TEST" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        creer(code, 500, false);

        mockMvc.perform(put("/api/v1/admin/billing/plans/" + code)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(code, 750, false, "AVAILABLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(750))
                .andExpect(jsonPath("$.priceLabel").value("750 FCFA / mois"));

        String token = accountOf("lecteur-" + UUID.randomUUID() + "@pulsetrack.test");
        mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].priceLabel")
                        .value("750 FCFA / mois"));

        supprimer(code);
    }

    /**
     * Une seule offre mise en avant. Deux rendraient indecidable celle que le
     * refus de paiement doit proposer — et la base la refuserait de toute façon.
     */
    @Test
    void transfere_la_mise_en_avant_au_lieu_de_la_dupliquer() throws Exception {
        String code = "STAR" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        creer(code, 900, true);

        mockMvc.perform(get("/api/v1/admin/billing/plans").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].highlighted").value(true))
                // YEARLY la detenait : elle doit l'avoir perdue, pas partagee.
                // L'unicite reelle est tenue par l'index unique partiel de la
                // base ; sans le transfert ci-dessus, la creation aurait echoue.
                .andExpect(jsonPath("$[?(@.code == 'YEARLY')].highlighted").value(false));

        supprimer(code);
        rendreLaMiseEnAvantAYEARLY();
    }

    @Test
    void refuse_deux_offres_sous_le_meme_code() throws Exception {
        String code = "DOUBLE" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        creer(code, 100, false);

        // Ecraser silencieusement ferait d'une faute de frappe une perte de donnees.
        mockMvc.perform(post("/api/v1/admin/billing/plans")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(code, 200, false, "COMING_SOON")))
                .andExpect(status().isUnprocessableEntity());

        supprimer(code);
    }

    /**
     * Le garde-fou qui compte : supprimer une offre souscrite laisserait des
     * abonnements pointant vers rien, et plus aucun ecran ne saurait dire ce que
     * ces comptes ont paye.
     */
    @Test
    void refuse_de_supprimer_une_offre_encore_souscrite() throws Exception {
        String code = "VENDU" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        creer(code, 1500, false);

        String abonneEmail = "abonne-" + UUID.randomUUID() + "@pulsetrack.test";
        accountOf(abonneEmail);
        UUID abonneId = users.findByEmail(abonneEmail).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/admin/users/" + abonneId + "/subscription")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE", "planCode": "%s", "note": "essai de suppression"}
                                """.formatted(code)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/billing/plans").header("Authorization", admin()))
                .andExpect(jsonPath("$[?(@.code == '" + code + "')].subscriberCount").value(1));

        mockMvc.perform(delete("/api/v1/admin/billing/plans/" + code)
                        .header("Authorization", admin()))
                .andExpect(status().isUnprocessableEntity())
                // Le message doit indiquer la sortie : passer l'offre en RETIRED.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("RETIRED")));

        // La base est partagee par toutes les classes de test : laisser cette
        // offre et cet abonnement derriere soi ferait echouer, plus tard et
        // ailleurs, un test qui compte les offres du catalogue.
        mockMvc.perform(put("/api/v1/admin/users/" + abonneId + "/subscription")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "NONE", "note": "nettoyage de test"}
                                """))
                .andExpect(status().isOk());
        supprimer(code);
    }

    /**
     * L'ecran affiche l'etat des deux verrous mais ne les bascule pas : ils
     * vivent chez l'hebergeur, et l'ordre entre eux n'est pas negociable.
     */
    @Test
    void publie_l_etat_de_la_mise_en_vente_en_lecture_seule() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/settings").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingEnforced").value(false))
                .andExpect(jsonPath("$.clientEnforced").value(false))
                .andExpect(jsonPath("$.trialDays").isNumber())
                .andExpect(jsonPath("$.minimumVersion").isNotEmpty());
    }

    private void creer(String code, long prix, boolean miseEnAvant) throws Exception {
        mockMvc.perform(post("/api/v1/admin/billing/plans")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps(code, prix, miseEnAvant, "COMING_SOON")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code));
    }

    /**
     * Remet le catalogue dans l'etat ou l'amorçage l'avait laisse.
     *
     * <p>La base est partagee par toutes les classes de test : sans ce retour en
     * arriere, une autre classe verifiant que l'offre annuelle est celle mise en
     * avant echouerait selon l'ordre d'execution — le pire genre d'echec, celui
     * qui ne se reproduit pas.
     */
    private void rendreLaMiseEnAvantAYEARLY() throws Exception {
        mockMvc.perform(put("/api/v1/admin/billing/plans/YEARLY")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Annuel",
                                  "description": "Deux mois offerts par rapport au mensuel.",
                                  "priceAmount": 20000,
                                  "currency": "FCFA",
                                  "period": "YEARLY",
                                  "availability": "COMING_SOON",
                                  "highlighted": true,
                                  "features": ["Tout le mensuel", "Deux mois offerts", "Export de tes données"],
                                  "displayOrder": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highlighted").value(true));
    }

    private void supprimer(String code) throws Exception {
        mockMvc.perform(delete("/api/v1/admin/billing/plans/" + code)
                        .header("Authorization", admin()))
                .andExpect(status().isNoContent());
    }

    private static String corps(String code, long prix, boolean miseEnAvant, String disponibilite) {
        return """
                {
                  "code": "%s",
                  "name": "Offre %s",
                  "description": "Offre de test.",
                  "priceAmount": %d,
                  "currency": "FCFA",
                  "period": "MONTHLY",
                  "availability": "%s",
                  "highlighted": %s,
                  "features": ["Un avantage", "Un autre"],
                  "displayOrder": 90
                }
                """.formatted(code, code, prix, disponibilite, miseEnAvant);
    }

    private String admin() throws Exception {
        return accountOf(ADMIN_EMAIL);
    }

    /**
     * S'inscrit, ou se connecte si le compte existe deja : la base n'est pas
     * remise a zero entre deux methodes, et l'adresse de l'administrateur est
     * fixe par nature.
     */
    private String accountOf(String email) throws Exception {
        String path = users.findByEmail(email).isPresent()
                ? "/api/v1/auth/login"
                : "/api/v1/auth/register";
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "motdepasse123"}
                                """.formatted(email)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }
}
