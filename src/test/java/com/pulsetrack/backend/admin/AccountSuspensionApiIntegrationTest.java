package com.pulsetrack.backend.admin;

import java.time.Duration;
import java.time.Instant;
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
 * Suspension d'un compte, et fiche d'usage de l'administration.
 *
 * <p>Le test qui porte tout le poids est
 * {@link #ferme_immediatement_un_jeton_deja_emis()} : sans lui, la suspension
 * ne serait qu'un drapeau en base, effectif au mieux vingt-quatre heures plus
 * tard — c'est-a-dire inutile au moment ou l'on s'en sert.
 */
@TestPropertySource(properties =
        "pulsetrack.access.admin-email=" + AccountSuspensionApiIntegrationTest.ADMIN_EMAIL)
class AccountSuspensionApiIntegrationTest extends AbstractApiIntegrationTest {

    static final String ADMIN_EMAIL = "gardien@pulsetrack.test";

    @Autowired
    private UserRepository users;

    /**
     * Le controle qui compte. Le jeton a ete emis avant la suspension et reste
     * cryptographiquement valide : c'est l'intercepteur, et lui seul, qui ferme
     * la porte.
     */
    @Test
    void ferme_immediatement_un_jeton_deja_emis() throws Exception {
        String email = "suspendu-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = accountOf(email);
        UUID id = idOf(email);

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isOk());

        suspendre(id, "Comportement inapproprié");

        mockMvc.perform(get("/api/v1/workouts").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://pulsetrack.app/problems/account-disabled"))
                // La raison voyage avec le refus : une porte fermee sans
                // explication ne laisse aucun recours.
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("Comportement inapproprié")));
    }

    @Test
    void refuse_la_connexion_puis_la_rouvre() throws Exception {
        String email = "porte-" + UUID.randomUUID() + "@pulsetrack.test";
        accountOf(email);
        UUID id = idOf(email);

        suspendre(id, "Essai de fermeture");

        // Pas 401 : le mot de passe etait bon, et faire retaper indefiniment un
        // mot de passe juste finirait par convaincre l'utilisateur de l'avoir
        // oublie.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value("https://pulsetrack.app/problems/account-disabled"));

        mockMvc.perform(put("/api/v1/admin/users/" + id + "/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled").value(false))
                .andExpect(jsonPath("$.disabledAt").doesNotExist())
                .andExpect(jsonPath("$.disabledReason").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().isOk());
    }

    /**
     * Etre suspendu d'une application de sport ne doit pas couter son propre
     * historique : l'export et la suppression de compte restent ouverts, comme
     * ils le restent derriere le mur de paiement.
     */
    @Test
    void laisse_recuperer_ses_donnees_malgre_la_suspension() throws Exception {
        String email = "portabilite-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = accountOf(email);
        UUID id = idOf(email);

        suspendre(id, "Vérification en cours");

        mockMvc.perform(get("/api/v1/me/export").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "motdepasse123"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void exige_une_raison_et_refuse_l_auto_suspension() throws Exception {
        String email = "raison-" + UUID.randomUUID() + "@pulsetrack.test";
        accountOf(email);
        UUID id = idOf(email);

        // Une suspension muette devient inexplicable six mois plus tard.
        mockMvc.perform(put("/api/v1/admin/users/" + id + "/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true, "reason": "   "}
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Se suspendre soi-meme, c'est fermer la porte qui permet de la rouvrir.
        mockMvc.perform(put("/api/v1/admin/users/" + idOf(ADMIN_EMAIL) + "/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true, "reason": "erreur de manipulation"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * La fiche porte des compteurs, jamais du contenu : savoir si un compte sert
     * encore ne demande pas de lire ou son proprietaire a couru.
     */
    @Test
    void rend_une_fiche_d_usage_et_l_abonnement_sans_donnee_personnelle() throws Exception {
        String email = "fiche-" + UUID.randomUUID() + "@pulsetrack.test";
        String token = accountOf(email);
        saveWorkout(token);
        UUID id = idOf(email);

        mockMvc.perform(get("/api/v1/admin/users/" + id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.account.disabled").value(false))
                .andExpect(jsonPath("$.usage.workoutCount").value(1))
                .andExpect(jsonPath("$.usage.workoutsLastSevenDays").value(1))
                .andExpect(jsonPath("$.usage.totalDistanceMeters").isNumber())
                .andExpect(jsonPath("$.usage.lastWorkoutAt").isNotEmpty())
                // L'abonnement voyage avec la fiche : la montrer en deux morceaux
                // ferait decider sur un etat a moitie charge.
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.accessGranted").value(true))
                // Rien du contenu sportif ne doit apparaitre.
                .andExpect(jsonPath("$.usage.gpsPoints").doesNotExist())
                .andExpect(jsonPath("$.account.weightKg").doesNotExist());
    }

    /**
     * Une seance d'il y a une heure.
     *
     * <p>Datee par rapport a maintenant et non par une date en dur : le
     * compteur « sept derniers jours » ferait sinon passer le test aujourd'hui
     * et echouer la semaine prochaine, sans que rien n'ait change.
     */
    private void saveWorkout(String token) throws Exception {
        // Le profil porte le poids, dont depend la depense energetique : sans
        // lui, l'enregistrement d'une seance n'a rien a quoi se rattacher.
        saveProfile(token, 70.0);

        Instant end = Instant.now().minus(Duration.ofHours(1));
        Instant start = end.minus(Duration.ofMinutes(30));
        mockMvc.perform(post("/api/v1/workouts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": "RUN",
                                  "startedAt": "%s",
                                  "endedAt": "%s",
                                  "distanceMeters": 5000
                                }
                                """.formatted(start, end)))
                .andExpect(status().isCreated());
    }

    private void suspendre(UUID id, String raison) throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + id + "/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true, "reason": "%s"}
                                """.formatted(raison)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled").value(true))
                .andExpect(jsonPath("$.disabledAt").isNotEmpty())
                .andExpect(jsonPath("$.disabledReason").value(raison));
    }

    private UUID idOf(String email) {
        return users.findByEmail(email).orElseThrow().getId();
    }

    private String admin() throws Exception {
        return accountOf(ADMIN_EMAIL);
    }

    private String accountOf(String email) throws Exception {
        String path = users.findByEmail(email).isPresent()
                ? "/api/v1/auth/login"
                : "/api/v1/auth/register";
        String response = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + json(response).get("accessToken").asText();
    }

    private static String credentials(String email) {
        return """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(email);
    }
}
