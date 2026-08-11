package com.pulsetrack.backend;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Socle des tests d'API : contexte Spring complet, vraie base PostgreSQL, et
 * requetes HTTP jouees via MockMvc.
 *
 * <p>Ces tests valident ce qu'un test unitaire ne peut pas voir : la chaine de
 * securite, la serialisation JSON, les transactions et les migrations Flyway.
 * Ils sont lents, on en garde donc peu, cibles sur les chemins critiques.
 *
 * <p>Le contexte Spring est mis en cache et partage entre les classes de test
 * qui heritent d'ici : le conteneur Postgres ne demarre qu'une fois.
 */
@SpringBootTest(properties = {
        AbstractApiIntegrationTest.NO_DOCKER_COMPOSE,
        AbstractApiIntegrationTest.NO_REMINDERS,
        AbstractApiIntegrationTest.NO_LOGIN_QUOTA,
        AbstractApiIntegrationTest.NO_REGISTER_QUOTA,
        AbstractApiIntegrationTest.NO_PASSWORD_RESET_QUOTA
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
public abstract class AbstractApiIntegrationTest {

    /** La base des tests vient de Testcontainers, pas de {@code compose.yaml}. */
    public static final String NO_DOCKER_COMPOSE = "spring.docker.compose.enabled=false";

    /**
     * Un traitement planifie qui se declenche au milieu d'une suite de tests la
     * rendrait non reproductible.
     */
    public static final String NO_REMINDERS = "pulsetrack.reminders.enabled=false";

    /**
     * MockMvc fait venir toutes les requetes de la meme adresse : avec les
     * plafonds reels, la suite entiere se ferait refuser des la sixieme
     * inscription. Les limites sont donc neutralisees ici, et eprouvees dans
     * {@code AuthRateLimitApiIntegrationTest}, qui redeclare {@code @SpringBootTest}
     * avec des valeurs basses.
     */
    public static final String NO_LOGIN_QUOTA = "pulsetrack.security.rate-limit.login.max-attempts=100000";

    /** Voir {@link #NO_LOGIN_QUOTA}. */
    public static final String NO_REGISTER_QUOTA = "pulsetrack.security.rate-limit.register.max-attempts=100000";

    /** Voir {@link #NO_LOGIN_QUOTA}. */
    public static final String NO_PASSWORD_RESET_QUOTA =
            "pulsetrack.security.rate-limit.password-reset.max-attempts=100000";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Cree un compte et retourne son jeton d'acces.
     *
     * <p>L'email est rendu unique a chaque appel : les tests partagent la meme
     * base, sans quoi le second test echouerait sur un conflit d'email.
     *
     * @return la valeur a placer dans l'en-tete {@code Authorization}
     */
    protected String registerUser() throws Exception {
        String email = "coureur-" + UUID.randomUUID() + "@pulsetrack.test";
        String body = """
                {"email": "%s", "password": "motdepasse123"}
                """.formatted(email);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return "Bearer " + json(response).get("accessToken").asText();
    }

    /**
     * Renseigne un profil minimal, prerequis a l'enregistrement d'une seance
     * puisque le calcul des calories a besoin du poids.
     */
    protected void saveProfile(String authorization, double weightKg) throws Exception {
        String body = """
                {
                  "displayName": "Nicolas",
                  "heightCm": 178,
                  "currentWeightKg": %s,
                  "primaryGoal": "IMPROVE_ENDURANCE",
                  "fitnessLevel": "INTERMEDIATE",
                  "preferredSports": ["RUN", "WALK"]
                }
                """.formatted(weightKg);

        mockMvc.perform(put("/api/v1/me/profile")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    protected JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }
}
