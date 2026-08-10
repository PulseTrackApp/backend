package com.pulsetrack.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Verifie que le contexte Spring demarre : cablage des beans, validation des
 * {@code @ConfigurationProperties}, et surtout execution des migrations Flyway
 * suivie du controle {@code ddl-auto: validate}. Une entite desynchronisee du
 * schema fait echouer ce test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
class BackendApplicationTests {

    @Test
    void le_contexte_demarre_et_les_migrations_correspondent_aux_entites() {
    }

}
