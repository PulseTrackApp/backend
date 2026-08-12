package com.pulsetrack.backend.user;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.error.RateLimitedException;
import com.pulsetrack.backend.common.ratelimit.FixedWindowRateLimiter;
import com.pulsetrack.backend.common.ratelimit.MutableClock;
import com.pulsetrack.backend.config.SecurityProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests des plafonds d'authentification, sans Spring.
 *
 * <p>Ce qui est verifie ici n'est pas le comptage lui-meme — c'est l'affaire de
 * {@code FixedWindowRateLimiterTest} — mais le <em>choix des cles</em> : c'est
 * lui qui decide de ce qu'un attaquant peut contourner.
 */
class AuthRateLimiterTest {

    private static final int LOGIN_MAX = 3;
    private static final int REGISTER_MAX = 2;
    private static final int RESET_MAX = 2;
    private static final int VERIFY_MAX = 2;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-10T10:00:00Z"));
    private final AuthRateLimiter rateLimiter =
            new AuthRateLimiter(new FixedWindowRateLimiter(clock), properties());

    @Test
    void bloque_les_connexions_repetees_depuis_une_meme_adresse() {
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            // Comptes vises differents : seule l'adresse est commune.
            rateLimiter.checkLogin("1.2.3.4", "victime-" + attempt + "@pulsetrack.test");
        }

        assertThatThrownBy(() -> rateLimiter.checkLogin("1.2.3.4", "encore@pulsetrack.test"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("Trop de tentatives");
    }

    @Test
    void bloque_les_connexions_repetees_sur_un_meme_compte_depuis_des_adresses_differentes() {
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            rateLimiter.checkLogin("10.0.0." + attempt, "nico@pulsetrack.test");
        }

        // Sans la cle par compte, changer d'adresse a chaque essai suffirait a
        // essayer un dictionnaire entier sur un compte donne.
        assertThatThrownBy(() -> rateLimiter.checkLogin("10.0.0.99", "nico@pulsetrack.test"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void compte_l_email_independamment_de_sa_casse() {
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            rateLimiter.checkLogin("10.0.0." + attempt, "nico@pulsetrack.test");
        }

        assertThatThrownBy(() -> rateLimiter.checkLogin("10.0.0.99", "  NICO@PulseTrack.TEST "))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void annonce_le_delai_avant_nouvelle_tentative() {
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test");
        }
        clock.advanceBy(Duration.ofMinutes(1));

        assertThatThrownBy(() -> rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test"))
                .isInstanceOfSatisfying(RateLimitedException.class, ex ->
                        assertThat(ex.retryAfter()).contains(LOGIN_WINDOW.minusMinutes(1)));
    }

    @Test
    void oublie_les_echecs_d_un_compte_apres_une_connexion_reussie() {
        rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test");
        rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test");

        rateLimiter.loginSucceeded("nico@pulsetrack.test");

        // Quelqu'un qui s'est trompe deux fois puis a retrouve son mot de passe
        // ne doit pas rester a un essai du blocage.
        assertThatCode(() -> rateLimiter.checkLogin("5.6.7.8", "nico@pulsetrack.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void ne_remet_pas_a_zero_le_quota_de_l_adresse_apres_une_connexion_reussie() {
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test");
        }

        rateLimiter.loginSucceeded("nico@pulsetrack.test");

        // Sinon, disposer d'un compte valide donnerait de quoi remettre son
        // quota a zero a volonte entre deux salves d'essais.
        assertThatThrownBy(() -> rateLimiter.checkLogin("1.2.3.4", "victime@pulsetrack.test"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void bloque_les_creations_de_compte_repetees_depuis_une_meme_adresse() {
        for (int attempt = 1; attempt <= REGISTER_MAX; attempt++) {
            rateLimiter.checkRegister("1.2.3.4");
        }

        assertThatThrownBy(() -> rateLimiter.checkRegister("1.2.3.4"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void bloque_les_demandes_de_reinitialisation_visant_une_meme_adresse() {
        for (int attempt = 1; attempt <= RESET_MAX; attempt++) {
            rateLimiter.checkPasswordResetRequest("10.0.0." + attempt, "victime@pulsetrack.test");
        }

        // Sans la cle par adresse visee, on pourrait inonder de courriels la
        // boite de quelqu'un d'autre en changeant d'IP a chaque envoi.
        assertThatThrownBy(() ->
                rateLimiter.checkPasswordResetRequest("10.0.0.99", "victime@pulsetrack.test"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void bloque_les_essais_de_code_repetes_depuis_une_meme_adresse() {
        for (int attempt = 1; attempt <= RESET_MAX; attempt++) {
            rateLimiter.checkPasswordResetAttempt("1.2.3.4");
        }

        // C'est ce plafond qui met la recherche exhaustive du code hors de
        // portee : il ne fait que huit caracteres.
        assertThatThrownBy(() -> rateLimiter.checkPasswordResetAttempt("1.2.3.4"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void separe_le_quota_de_reinitialisation_de_celui_de_connexion() {
        for (int attempt = 1; attempt <= RESET_MAX; attempt++) {
            rateLimiter.checkPasswordResetAttempt("1.2.3.4");
        }

        assertThatCode(() -> rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void separe_le_quota_de_verification_d_adresse_de_celui_de_reinitialisation() {
        for (int attempt = 1; attempt <= VERIFY_MAX; attempt++) {
            rateLimiter.checkEmailVerificationAttempt("1.2.3.4");
        }

        // Quelqu'un qui s'acharne a confirmer son adresse ne doit pas y perdre
        // son droit a demander une reinitialisation : c'est le seul recours en
        // cas d'oubli du mot de passe.
        assertThatCode(() -> rateLimiter.checkPasswordResetAttempt("1.2.3.4"))
                .doesNotThrowAnyException();
    }

    @Test
    void bloque_les_demandes_de_verification_visant_une_meme_adresse() {
        for (int attempt = 1; attempt <= VERIFY_MAX; attempt++) {
            rateLimiter.checkEmailVerificationRequest("10.0.0." + attempt, "victime@pulsetrack.test");
        }

        assertThatThrownBy(() ->
                rateLimiter.checkEmailVerificationRequest("10.0.0.99", "victime@pulsetrack.test"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void plafonne_les_essais_de_mot_de_passe_actuel_par_compte() {
        UUID compte = UUID.fromString("11111111-2222-3333-4444-555555555555");
        for (int attempt = 1; attempt <= LOGIN_MAX; attempt++) {
            rateLimiter.checkPasswordChange(compte);
        }

        // La cle est le compte et non l'adresse IP : l'appelant est
        // authentifie, et c'est son jeton — vole ou non — qu'on plafonne.
        assertThatThrownBy(() -> rateLimiter.checkPasswordChange(compte))
                .isInstanceOf(RateLimitedException.class);
        assertThatCode(() -> rateLimiter.checkPasswordChange(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void separe_le_quota_d_inscription_de_celui_de_connexion() {
        for (int attempt = 1; attempt <= REGISTER_MAX; attempt++) {
            rateLimiter.checkRegister("1.2.3.4");
        }

        // Une inscription saturee ne doit pas empecher de se connecter.
        assertThatCode(() -> rateLimiter.checkLogin("1.2.3.4", "nico@pulsetrack.test"))
                .doesNotThrowAnyException();
    }

    private static SecurityProperties properties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt("secret-de-test-suffisamment-long-0123456789",
                        "pulsetrack", Duration.ofHours(1)),
                new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                new SecurityProperties.PasswordReset(Duration.ofMinutes(30)),
                new SecurityProperties.EmailVerification(false, Duration.ofHours(24)),
                new SecurityProperties.RateLimit(
                        new SecurityProperties.RateLimit.Policy(LOGIN_MAX, LOGIN_WINDOW),
                        new SecurityProperties.RateLimit.Policy(REGISTER_MAX, Duration.ofHours(1)),
                        new SecurityProperties.RateLimit.Policy(RESET_MAX, Duration.ofMinutes(15)),
                        new SecurityProperties.RateLimit.Policy(VERIFY_MAX, Duration.ofMinutes(15))),
                new SecurityProperties.Cors(List.of("http://localhost:3000")),
                new SecurityProperties.Encryption("mot-de-passe-de-test", "a1b2c3d4"));
    }
}
