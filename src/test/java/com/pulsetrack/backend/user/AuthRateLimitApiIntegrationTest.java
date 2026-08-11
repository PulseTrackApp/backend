package com.pulsetrack.backend.user;

import java.util.UUID;

import com.pulsetrack.backend.AbstractApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plafonds des endpoints ouverts, joues de bout en bout.
 *
 * <p>Les limites sont abaissees ici : le socle commun les neutralise, faute de
 * quoi la suite entiere se ferait refuser au bout de quelques inscriptions,
 * toutes ses requetes venant de la meme adresse.
 *
 * <p>Chaque test part d'une adresse distincte. Les compteurs vivent dans le
 * contexte Spring, partage entre les methodes : sans cela, le premier test a
 * saturer son quota ferait echouer les suivants.
 *
 * <p>{@code @SpringBootTest} est redeclare, et non complete par un
 * {@code @TestPropertySource} : Spring Boot applique les proprietes de
 * {@code @SpringBootTest} <em>apres</em> celles de {@code @TestPropertySource},
 * de sorte que les plafonds neutralises par le socle l'emporteraient.
 */
@SpringBootTest(properties = {
        AbstractApiIntegrationTest.NO_DOCKER_COMPOSE,
        AbstractApiIntegrationTest.NO_REMINDERS,
        "pulsetrack.security.rate-limit.login.max-attempts=3",
        "pulsetrack.security.rate-limit.login.window=PT5M",
        "pulsetrack.security.rate-limit.register.max-attempts=2",
        "pulsetrack.security.rate-limit.register.window=PT1H",
        "pulsetrack.security.rate-limit.password-reset.max-attempts=2",
        "pulsetrack.security.rate-limit.password-reset.window=PT15M"
})
class AuthRateLimitApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void refuse_les_connexions_au_dela_du_plafond() throws Exception {
        RequestPostProcessor client = fromIp("203.0.113.1");

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(client)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(uniqueEmail(), "motdepasse123")))
                    // Identifiants inconnus : ce qui compte ici est que la
                    // requete soit traitee, pas qu'elle reussisse.
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login").with(client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Quota atteint"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void refuse_les_inscriptions_au_dela_du_plafond() throws Exception {
        RequestPostProcessor client = fromIp("203.0.113.2");

        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/register").with(client)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(uniqueEmail(), "motdepasse123")))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/auth/register").with(client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void plafonne_les_tentatives_visant_un_meme_compte_depuis_des_adresses_differentes() throws Exception {
        String target = uniqueEmail();

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(fromIp("198.51.100." + attempt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(target, "essai" + attempt)))
                    .andExpect(status().isUnauthorized());
        }

        // Changer d'adresse a chaque essai ne doit pas suffire a derouler un
        // dictionnaire sur un compte donne.
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp("198.51.100.99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(target, "essai-de-trop")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void n_impose_pas_le_quota_d_une_adresse_a_une_autre() throws Exception {
        RequestPostProcessor saturated = fromIp("203.0.113.3");
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(saturated)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(uniqueEmail(), "motdepasse123")));
        }

        mockMvc.perform(post("/api/v1/auth/login").with(fromIp("203.0.113.4"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), "motdepasse123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ne_penalise_pas_un_compte_apres_une_connexion_reussie() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register").with(fromIp("203.0.113.5"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isCreated());

        // Deux erreurs de saisie, puis le bon mot de passe : le compteur du
        // compte repart de zero.
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(fromIp("203.0.113." + (10 + attempt)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(email, "mauvais")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(fromIp("203.0.113.13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").with(fromIp("203.0.113.14"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "motdepasse123")))
                .andExpect(status().isOk());
    }

    @Test
    void refuse_les_demandes_de_reinitialisation_au_dela_du_plafond() throws Exception {
        String cible = uniqueEmail();

        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password").with(fromIp("203.0.113." + (20 + attempt)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "%s"}
                                    """.formatted(cible)))
                    .andExpect(status().isNoContent());
        }

        // Adresses differentes, meme cible : sans plafond par adresse visee, on
        // inonderait la boite aux lettres de quelqu'un d'autre.
        mockMvc.perform(post("/api/v1/auth/forgot-password").with(fromIp("203.0.113.29"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(cible)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refuse_les_essais_de_code_au_dela_du_plafond() throws Exception {
        RequestPostProcessor client = fromIp("203.0.113.30");

        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/reset-password").with(client)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code": "ABCD234%d", "newPassword": "motdepasse123"}
                                    """.formatted(attempt)))
                    .andExpect(status().isBadRequest());
        }

        // C'est ce plafond qui met hors de portee la recherche exhaustive d'un
        // code de huit caracteres.
        mockMvc.perform(post("/api/v1/auth/reset-password").with(client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "ABCD2349", "newPassword": "motdepasse123"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * MockMvc fait venir toutes les requetes de {@code 127.0.0.1} : sans ce
     * reglage, chaque test consommerait le quota des autres.
     */
    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String uniqueEmail() {
        return "quota-" + UUID.randomUUID() + "@pulsetrack.test";
    }

    private String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
