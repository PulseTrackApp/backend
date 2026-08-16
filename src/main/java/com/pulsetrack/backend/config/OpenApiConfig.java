package com.pulsetrack.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/**
 * Description OpenAPI de l'API, servie par Swagger UI.
 *
 * <p>Le schema de securite declare permet d'utiliser le bouton « Authorize » de
 * Swagger UI : on y colle le jeton renvoye par {@code /api/v1/auth/login}, et
 * toutes les requetes suivantes le portent automatiquement.
 *
 * <p>La documentation est desactivee par le profil {@code prod} : elle decrit la
 * surface d'attaque de l'API et n'a rien a faire en ligne.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PulseTrack API",
                version = "v1",
                description = "Suivi sportif personnel : comptes, profils et séances."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
