package com.pulsetrack.backend.summary;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la serie de jours actifs, sans Spring.
 */
class ActivityStreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    private final ActivityStreakCalculator calculator = new ActivityStreakCalculator();

    @Test
    void repond_zero_sans_aucune_seance() {
        assertThat(calculator.streakOf(List.of(), TODAY)).isZero();
    }

    @Test
    void compte_les_jours_consecutifs_jusqu_a_aujourd_hui() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(calculator.streakOf(days, TODAY)).isEqualTo(3);
    }

    @Test
    void ne_casse_pas_la_serie_tant_que_la_journee_n_est_pas_finie() {
        // Rien couru aujourd'hui a 8 h du matin : la serie d'hier tient toujours.
        List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));

        assertThat(calculator.streakOf(days, TODAY)).isEqualTo(3);
    }

    @Test
    void s_arrete_au_premier_jour_manquant() {
        List<LocalDate> days = List.of(
                TODAY, TODAY.minusDays(1),
                // trou le jour 2
                TODAY.minusDays(3), TODAY.minusDays(4));

        assertThat(calculator.streakOf(days, TODAY)).isEqualTo(2);
    }

    @Test
    void considere_la_serie_rompue_apres_deux_jours_sans_activite() {
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));

        assertThat(calculator.streakOf(days, TODAY)).isZero();
    }

    @Test
    void ne_depend_pas_de_l_ordre_des_jours_recus() {
        List<LocalDate> shuffled = List.of(TODAY.minusDays(2), TODAY, TODAY.minusDays(1));

        assertThat(calculator.streakOf(shuffled, TODAY)).isEqualTo(3);
    }
}
