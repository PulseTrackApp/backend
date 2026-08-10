package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du jeton de renouvellement, sans Spring ni base.
 */
class RefreshTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofDays(30));

    @Test
    void est_actif_a_l_emission() {
        RefreshToken token = newToken();

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.hasExpiredAt(ISSUED_AT)).isFalse();
    }

    @Test
    void expire_des_l_instant_d_expiration_atteint() {
        RefreshToken token = newToken();

        // Borne exclusive : a la seconde pile, le jeton ne vaut plus rien.
        assertThat(token.hasExpiredAt(EXPIRES_AT.minusMillis(1))).isFalse();
        assertThat(token.hasExpiredAt(EXPIRES_AT)).isTrue();
    }

    @Test
    void conserve_la_date_de_la_premiere_revocation() {
        RefreshToken token = newToken();
        Instant firstLogout = ISSUED_AT.plus(Duration.ofDays(1));

        token.revokeAt(firstLogout);
        token.revokeAt(ISSUED_AT.plus(Duration.ofDays(2)));

        // Une deconnexion rejouee ne doit pas effacer la trace de la premiere.
        assertThat(token.getRevokedAt()).isEqualTo(firstLogout);
        assertThat(token.isRevoked()).isTrue();
    }

    private RefreshToken newToken() {
        return new RefreshToken(UUID.randomUUID(), "empreinte", ISSUED_AT, EXPIRES_AT);
    }
}
