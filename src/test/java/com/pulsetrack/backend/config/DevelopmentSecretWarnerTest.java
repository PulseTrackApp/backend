package com.pulsetrack.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la reconnaissance d'un secret d'exemple, sans Spring.
 */
class DevelopmentSecretWarnerTest {

    @Test
    void reconnait_le_secret_livre_dans_le_depot() {
        assertThat(DevelopmentSecretWarner.isDevelopmentSecret(
                "dev-only-secret-change-me-0123456789-abcdefghijklmnopqrstuvwxyz")).isTrue();
    }

    @Test
    void reconnait_toute_valeur_portant_le_prefixe_de_convention() {
        // Retoucher la valeur d'application.yml ne doit pas rendre le controle muet.
        assertThat(DevelopmentSecretWarner.isDevelopmentSecret("dev-only-autre-chose")).isTrue();
    }

    @Test
    void ne_signale_pas_un_secret_de_production() {
        assertThat(DevelopmentSecretWarner.isDevelopmentSecret(
                "Yz7kQm2X9pLdR4vN8sT1wC6hB3jF5gA0eU7iO2yK4nM=")).isFalse();
    }

    @Test
    void tolere_l_absence_de_secret() {
        // La validation de configuration s'en charge deja et fait echouer le
        // demarrage : ce controle-ci ne doit pas exploser avant elle.
        assertThat(DevelopmentSecretWarner.isDevelopmentSecret(null)).isFalse();
    }
}
