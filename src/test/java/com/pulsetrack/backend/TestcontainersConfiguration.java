package com.pulsetrack.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Demarre un vrai PostgreSQL pour les tests, au lieu d'une base H2 en memoire.
 *
 * <p>H2 accepte du SQL que Postgres refuse, et inversement : tester dessus
 * laisserait passer des migrations Flyway cassees jusqu'en production. L'image
 * est la meme que celle de {@code compose.yaml}.
 *
 * <p>{@code @ServiceConnection} cable automatiquement la datasource sur le
 * conteneur : aucune propriete d'URL a ecrire.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

}
