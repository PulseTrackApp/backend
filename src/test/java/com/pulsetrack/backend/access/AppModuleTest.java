package com.pulsetrack.backend.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correspondance entre une route et le module qui la protege, sans Spring.
 */
class AppModuleTest {

    @ParameterizedTest
    @EnumSource(AppModule.class)
    void reconnait_sa_propre_route_racine(AppModule module) {
        assertThat(AppModule.forPath(module.pathPrefix())).contains(module);
    }

    @ParameterizedTest
    @EnumSource(AppModule.class)
    void reconnait_ses_sous_routes(AppModule module) {
        assertThat(AppModule.forPath(module.pathPrefix() + "/123/gps-points")).contains(module);
    }

    /**
     * Le piege que corrige la comparaison sur la frontiere : un simple
     * {@code startsWith} verrouillerait une route qui se contente de commencer
     * comme celle d'un module, et refuserait l'acces a une fonctionnalite qui
     * n'a rien a voir.
     */
    @Test
    void ne_confond_pas_une_route_qui_commence_par_le_meme_prefixe() {
        assertThat(AppModule.forPath("/api/v1/me/statistiques-publiques")).isEmpty();
        assertThat(AppModule.forPath("/api/v1/workouts-archives")).isEmpty();
    }

    /**
     * Le noyau ne doit dependre d'aucun module : un administrateur qui ferme
     * tout ne doit pas pouvoir enfermer quelqu'un hors de son propre compte.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/me/profile",
            "/api/v1/me/modules",
            "/actuator/health"
    })
    void laisse_passer_le_noyau(String path) {
        assertThat(AppModule.forPath(path)).isEmpty();
    }

    @Test
    void tolere_un_chemin_absent() {
        assertThat(AppModule.forPath(null)).isEmpty();
    }
}
