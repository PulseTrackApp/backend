package com.pulsetrack.backend.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du compteur de tentatives, sans Spring.
 */
class FixedWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-10T10:00:00Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);

    @Test
    void accepte_les_tentatives_jusqu_a_la_limite() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertThat(consume("ip:1.2.3.4"))
                    .as("tentative %d", attempt)
                    .isEmpty();
        }
    }

    @Test
    void refuse_au_dela_de_la_limite_et_annonce_le_delai_restant() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            consume("ip:1.2.3.4");
        }
        clock.advanceBy(Duration.ofMinutes(2));

        assertThat(consume("ip:1.2.3.4")).contains(Duration.ofMinutes(3));
    }

    @Test
    void rouvre_la_fenetre_une_fois_ecoulee() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            consume("ip:1.2.3.4");
        }

        clock.advanceBy(WINDOW);

        assertThat(consume("ip:1.2.3.4")).isEmpty();
    }

    @Test
    void compte_chaque_cle_separement() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            consume("ip:1.2.3.4");
        }

        // Un attaquant qui sature son quota ne doit pas bloquer les autres.
        assertThat(consume("ip:5.6.7.8")).isEmpty();
    }

    @Test
    void compte_aussi_les_tentatives_deja_refusees() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 5; attempt++) {
            consume("ip:1.2.3.4");
        }
        // Insister ne doit pas rouvrir la fenetre plus tot : le delai annonce
        // reste celui de la fenetre ouverte a la premiere tentative.
        clock.advanceBy(WINDOW.minusSeconds(1));

        assertThat(consume("ip:1.2.3.4")).contains(Duration.ofSeconds(1));
    }

    @Test
    void oublie_une_cle_remise_a_zero() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            consume("email:nico@pulsetrack.test");
        }

        limiter.reset("email:nico@pulsetrack.test");

        assertThat(consume("email:nico@pulsetrack.test")).isEmpty();
    }

    @Test
    void purge_les_fenetres_expirees_plutot_que_de_grossir_indefiniment() {
        // Le seuil de purge est a 10 000 cles : au-dela, une avalanche d'adresses
        // distinctes ferait du limiteur lui-meme le moyen de saturer la memoire.
        for (int key = 0; key < 10_000; key++) {
            consume("ip:10.0." + (key / 256) + "." + (key % 256));
        }
        assertThat(limiter.trackedKeys()).isEqualTo(10_000);

        clock.advanceBy(WINDOW);
        consume("ip:1.2.3.4");

        // Tout a expire d'un coup : il ne reste que la cle qui vient de declencher la purge.
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    private Optional<Duration> consume(String key) {
        return limiter.tryConsume(key, MAX_ATTEMPTS, WINDOW);
    }
}
