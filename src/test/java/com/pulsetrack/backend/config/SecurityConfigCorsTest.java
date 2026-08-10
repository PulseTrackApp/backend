package com.pulsetrack.backend.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des autorisations CORS, sans contexte Spring.
 *
 * <p>Le point sensible est le cas « aucune origine declaree » : sur un projet
 * mobile il n'y a pas de front web, et la configuration doit alors ne rien
 * autoriser — surtout pas tout autoriser.
 */
class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void n_enregistre_aucune_regle_quand_aucune_origine_n_est_declaree() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(properties(List.of()));

        // Aucune regle : le filtre n'ajoute aucun en-tete, et le navigateur
        // bloque la reponse de lui-meme. Liste vide vaut « aucune », pas « toutes ».
        assertThat(source.getCorsConfiguration(request("/api/v1/workouts"))).isNull();
    }

    @Test
    void applique_les_origines_declarees() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(properties(List.of("https://gymflow.example.com")));

        CorsConfiguration configuration = source.getCorsConfiguration(request("/api/v1/workouts"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://gymflow.example.com");
    }

    @Test
    void n_autorise_jamais_les_credentials() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(properties(List.of("https://gymflow.example.com")));

        // L'authentification passe par l'en-tete Authorization, pas par un
        // cookie : autoriser les credentials n'apporterait rien et interdirait
        // par ailleurs toute origine joker.
        assertThat(source.getCorsConfiguration(request("/api/v1/workouts")).getAllowCredentials())
                .isFalse();
    }

    @Test
    void ne_couvre_que_les_chemins_de_l_api() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource(properties(List.of("https://gymflow.example.com")));

        assertThat(source.getCorsConfiguration(request("/actuator/health"))).isNull();
    }

    @Test
    void accepte_une_configuration_sans_aucune_origine() {
        // Le vrai filet : si `@NotEmpty` revenait sur `allowedOrigins`, un
        // deploiement mobile sans front web refuserait de demarrer.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<SecurityProperties>> violations =
                    validator.validate(properties(List.of()));

            assertThat(violations).isEmpty();
        }
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static SecurityProperties properties(List<String> allowedOrigins) {
        return new SecurityProperties(
                new SecurityProperties.Jwt("secret-de-test-suffisamment-long-0123456789",
                        "pulsetrack", Duration.ofHours(1)),
                new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                new SecurityProperties.RateLimit(
                        new SecurityProperties.RateLimit.Policy(10, Duration.ofMinutes(5)),
                        new SecurityProperties.RateLimit.Policy(5, Duration.ofHours(1))),
                new SecurityProperties.Cors(allowedOrigins),
                new SecurityProperties.Encryption("mot-de-passe-de-test", "a1b2c3d4"));
    }
}
