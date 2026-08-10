package com.pulsetrack.backend;

import org.springframework.boot.SpringApplication;

/**
 * Lance l'application en developpement avec une base jetable geree par
 * Testcontainers, sans dependre de {@code docker compose}.
 *
 * <p>Utile pour explorer l'API sur une base vierge :
 * {@code ./mvnw spring-boot:test-run}.
 */
public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
