package com.pulsetrack.backend.challenge;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeProgressResponse.AlertLevel;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.motivation.AppreciationTier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les seuils du mode defi, eprouves avec des chiffres choisis.
 *
 * <p>Le point le plus important est la dissymetrie des tolerances : un pour cent
 * de marge sur la distance parce que le GPS ne rend pas le metre exact, et
 * aucune sur le temps parce qu'une echeance qui pardonne n'est plus une echeance.
 */
class ChallengeEvaluatorTest {

    private final ChallengeEvaluator evaluator = new ChallengeEvaluator();

    /** 10 km en 55 minutes, soit 5:30/km. */
    private final Challenge challenge = challenge(10_000, 3_300);

    // -----------------------------------------------------------------------
    // Verdict final
    // -----------------------------------------------------------------------

    @Test
    void declare_reussi_un_defi_couvert_dans_les_temps() {
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 10_120, 3_280, false);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.timeMarginSeconds()).isEqualTo(20);
        assertThat(result.distanceMarginMeters()).isEqualTo(120d);
        assertThat(result.celebrate()).isTrue();
        assertThat(result.appreciation().tier()).isEqualTo(AppreciationTier.EXCELLENT);
    }

    @Test
    void tolere_un_pour_cent_de_distance_manquante() {
        // 9 950 metres sur 10 000 : le GPS ne rend pas 10 000,0. Refuser ici
        // serait vecu comme une injustice.
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 9_950, 3_280, false);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void ne_tolere_aucun_depassement_de_l_echeance() {
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 10_200, 3_301, false);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.timeMarginSeconds()).isEqualTo(-1);
    }

    @Test
    void felicite_un_echec_de_peu_quand_un_record_est_tombe() {
        // Manque le defi de dix secondes, mais bat un record au passage : un
        // ecran rouge serait la pire reponse possible.
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 10_000, 3_310, true);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.celebrate()).isTrue();
        assertThat(result.appreciation().tier()).isEqualTo(AppreciationTier.BEHIND);
    }

    @Test
    void ne_felicite_pas_un_echec_de_peu_sans_record() {
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 10_000, 3_310, false);

        assertThat(result.celebrate()).isFalse();
    }

    @Test
    void ne_felicite_pas_un_echec_franc_meme_avec_un_record() {
        ChallengeResponse.Result result = evaluator.evaluate(challenge, 6_000, 3_300, true);

        assertThat(result.celebrate()).isFalse();
        assertThat(result.appreciation().headline()).isEqualTo("Distance non couverte");
    }

    @Test
    void distingue_la_distance_non_couverte_de_l_echeance_depassee() {
        ChallengeResponse.Result tooSlow = evaluator.evaluate(challenge, 10_100, 3_600, false);
        ChallengeResponse.Result tooShort = evaluator.evaluate(challenge, 7_000, 3_000, false);

        assertThat(tooSlow.appreciation().headline()).isEqualTo("Distance faite, échéance dépassée");
        assertThat(tooShort.appreciation().headline()).isEqualTo("Distance non couverte");
    }

    @Test
    void ne_propose_un_cran_superieur_qu_apres_une_reussite_confortable() {
        ChallengeResponse.Result comfortable = evaluator.evaluate(challenge, 10_000, 2_900, false);
        ChallengeResponse.Result tight = evaluator.evaluate(challenge, 10_000, 3_295, false);

        assertThat(comfortable.appreciation().advice()).contains("prochain cran");
        assertThat(tight.appreciation().advice()).isNull();
    }

    // -----------------------------------------------------------------------
    // Point d'etape
    // -----------------------------------------------------------------------

    @Test
    void ne_signale_rien_quand_le_rythme_mene_au_bout() {
        // La moitie du temps, la moitie de la distance : exactement dans les clous.
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 1_650, 5_000);

        assertThat(progress.onTrack()).isTrue();
        assertThat(progress.alertLevel()).isEqualTo(AlertLevel.NONE);
        assertThat(progress.deltaSeconds()).isZero();
    }

    @Test
    void calcule_l_allure_a_tenir_sur_ce_qui_reste_et_non_sur_le_defi_entier() {
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 1_200, 3_400);

        // 6 600 metres en 2 100 secondes : quelqu'un parti trop lentement doit
        // savoir a quel rythme il rattrape, pas a quel rythme il aurait fallu
        // partir.
        assertThat(progress.remainingDistanceMeters()).isEqualTo(6_600d);
        assertThat(progress.remainingSeconds()).isEqualTo(2_100);
        assertThat(progress.requiredPaceSecondsPerKm()).isEqualTo(318);
        assertThat(progress.requiredPaceSecondsPerKm())
                .isNotEqualTo(challenge.requiredPaceSecondsPerKm());
    }

    @Test
    void alerte_progressivement_selon_l_effort_a_fournir() {
        // Un peu en retard : l'ecart se comble.
        assertThat(evaluator.progressOf(challenge, 1_700, 5_000).alertLevel())
                .isEqualTo(AlertLevel.WATCH);

        // Il faudrait passer de 6:40/km a 4:20/km : l'echeance est en jeu.
        assertThat(evaluator.progressOf(challenge, 2_000, 5_000).alertLevel())
                .isEqualTo(AlertLevel.URGENT);

        // Il faudrait aller dix fois plus vite : le dire franchement vaut mieux
        // qu'un encouragement absurde.
        assertThat(evaluator.progressOf(challenge, 3_000, 5_000).alertLevel())
                .isEqualTo(AlertLevel.LOST);
    }

    @Test
    void declare_perdu_quand_le_temps_est_ecoule_sans_la_distance() {
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 3_300, 8_000);

        assertThat(progress.alertLevel()).isEqualTo(AlertLevel.LOST);
        // Ni faux espoir, ni reproche : on propose une sortie honorable.
        assertThat(progress.message()).contains("la distance, si");
    }

    @Test
    void considere_la_distance_faite_des_que_la_tolerance_est_atteinte() {
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 3_290, 9_950);

        assertThat(progress.alertLevel()).isEqualTo(AlertLevel.NONE);
        assertThat(progress.headline()).isEqualTo("Distance couverte");
    }

    @Test
    void avertit_sans_calculer_d_allure_quand_rien_n_a_encore_ete_parcouru() {
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 600, 0);

        assertThat(progress.currentPaceSecondsPerKm()).isNull();
        assertThat(progress.alertLevel()).isEqualTo(AlertLevel.WATCH);
        assertThat(progress.projectedFinishSeconds()).isNull();
    }

    @Test
    void ne_leve_pas_d_erreur_au_tout_premier_instant() {
        ChallengeProgressResponse progress = evaluator.progressOf(challenge, 0, 0);

        assertThat(progress.completionPercent()).isZero();
        assertThat(progress.remainingSeconds()).isEqualTo(3_300);
    }

    private Challenge challenge(double distanceMeters, long durationSeconds) {
        return new Challenge(UUID.randomUUID(), UUID.randomUUID(), "10 km en 55 min",
                SportType.RUN, distanceMeters, durationSeconds, null, null,
                Instant.parse("2026-08-15T06:00:00Z"));
    }
}
