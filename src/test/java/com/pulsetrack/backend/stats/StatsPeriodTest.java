package com.pulsetrack.backend.stats;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des bornes de periode. Les erreurs de calendrier (fevrier, annees
 * bissextiles, dimanche) sont classiques : autant les figer.
 */
class StatsPeriodTest {

    @Test
    void la_semaine_va_du_lundi_au_dimanche() {
        // Le 6 aout 2026 est un jeudi.
        LocalDate start = StatsPeriod.WEEK.startOf(LocalDate.of(2026, 8, 6));

        assertThat(start).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(StatsPeriod.WEEK.endOf(start)).isEqualTo(LocalDate.of(2026, 8, 9));
    }

    @Test
    void un_dimanche_appartient_a_la_semaine_qui_s_acheve() {
        // Piege classique : le dimanche 9 aout ne doit pas ouvrir une semaine.
        assertThat(StatsPeriod.WEEK.startOf(LocalDate.of(2026, 8, 9)))
                .isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    void un_lundi_est_deja_le_debut_de_sa_semaine() {
        assertThat(StatsPeriod.WEEK.startOf(LocalDate.of(2026, 8, 3)))
                .isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    void le_mois_couvre_sa_longueur_reelle() {
        LocalDate start = StatsPeriod.MONTH.startOf(LocalDate.of(2026, 2, 17));

        assertThat(start).isEqualTo(LocalDate.of(2026, 2, 1));
        // 2026 n'est pas bissextile.
        assertThat(StatsPeriod.MONTH.endOf(start)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void gere_une_annee_bissextile() {
        LocalDate start = StatsPeriod.MONTH.startOf(LocalDate.of(2028, 2, 10));

        assertThat(StatsPeriod.MONTH.endOf(start)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void l_annee_va_du_premier_janvier_au_trente_et_un_decembre() {
        LocalDate start = StatsPeriod.YEAR.startOf(LocalDate.of(2026, 8, 6));

        assertThat(start).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(StatsPeriod.YEAR.endOf(start)).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void la_periode_precedente_recule_d_une_unite() {
        assertThat(StatsPeriod.WEEK.previousStartOf(LocalDate.of(2026, 8, 3)))
                .isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(StatsPeriod.MONTH.previousStartOf(LocalDate.of(2026, 3, 1)))
                .isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(StatsPeriod.YEAR.previousStartOf(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void depuis_le_debut_n_a_pas_de_periode_precedente() {
        assertThat(StatsPeriod.LIFETIME.previousStartOf(LocalDate.of(2026, 8, 3))).isNull();
    }

    @Test
    void le_pas_de_la_serie_depend_de_la_periode() {
        assertThat(StatsPeriod.WEEK.bucketSize()).isEqualTo(StatsPeriod.BucketSize.DAY);
        assertThat(StatsPeriod.MONTH.bucketSize()).isEqualTo(StatsPeriod.BucketSize.DAY);
        assertThat(StatsPeriod.YEAR.bucketSize()).isEqualTo(StatsPeriod.BucketSize.MONTH);
        assertThat(StatsPeriod.LIFETIME.bucketSize()).isEqualTo(StatsPeriod.BucketSize.MONTH);
    }
}
