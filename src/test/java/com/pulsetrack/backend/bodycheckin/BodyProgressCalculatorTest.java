package com.pulsetrack.backend.bodycheckin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des indicateurs d'evolution physique, sans Spring.
 */
class BodyProgressCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final Integer HEIGHT_CM = 178;

    private final BodyProgressCalculator calculator = new BodyProgressCalculator();

    @Test
    void n_annonce_aucune_tendance_sans_releve() {
        BodyProgressCalculator.Indicators indicators = calculator.calculate(List.of(), HEIGHT_CM);

        assertThat(indicators.trend()).isEqualTo(WeightTrend.NOT_ENOUGH_DATA);
        assertThat(indicators.currentWeightKg()).isNull();
        assertThat(indicators.totalChangeKg()).isNull();
    }

    @Test
    void avec_un_seul_releve_donne_le_poids_mais_pas_de_variation() {
        BodyProgressCalculator.Indicators indicators =
                calculator.calculate(List.of(checkIn(START, 80.0)), HEIGHT_CM);

        assertThat(indicators.currentWeightKg()).isEqualTo(80.0);
        assertThat(indicators.trend()).isEqualTo(WeightTrend.NOT_ENOUGH_DATA);
        // Nul et non zero : il n'y a pas de stagnation mesuree, il n'y a rien de mesure.
        assertThat(indicators.totalChangeKg()).isNull();
        assertThat(indicators.averageWeeklyChangeKg()).isNull();
    }

    @Test
    void detecte_une_perte_de_poids_et_son_rythme_hebdomadaire() {
        // 2 kg perdus en 4 semaines, soit 0,5 kg par semaine.
        List<BodyCheckIn> series = List.of(
                checkIn(START, 80.0),
                checkIn(START.plusWeeks(2), 79.0),
                checkIn(START.plusWeeks(4), 78.0));

        BodyProgressCalculator.Indicators indicators = calculator.calculate(series, HEIGHT_CM);

        assertThat(indicators.startWeightKg()).isEqualTo(80.0);
        assertThat(indicators.currentWeightKg()).isEqualTo(78.0);
        assertThat(indicators.totalChangeKg()).isEqualTo(-2.0);
        assertThat(indicators.changeSincePreviousKg()).isEqualTo(-1.0);
        assertThat(indicators.averageWeeklyChangeKg()).isEqualTo(-0.5);
        assertThat(indicators.trend()).isEqualTo(WeightTrend.LOSING);
    }

    @Test
    void detecte_une_prise_de_poids() {
        List<BodyCheckIn> series = List.of(
                checkIn(START, 70.0),
                checkIn(START.plusWeeks(4), 72.0));

        assertThat(calculator.calculate(series, HEIGHT_CM).trend()).isEqualTo(WeightTrend.GAINING);
    }

    @Test
    void considere_stable_une_variation_sous_le_bruit_de_la_balance() {
        // 200 g sur 8 semaines : 25 g par semaine, indiscernable du bruit.
        List<BodyCheckIn> series = List.of(
                checkIn(START, 75.0),
                checkIn(START.plusWeeks(8), 75.2));

        BodyProgressCalculator.Indicators indicators = calculator.calculate(series, HEIGHT_CM);

        assertThat(indicators.trend()).isEqualTo(WeightTrend.STABLE);
        assertThat(indicators.totalChangeKg()).isEqualTo(0.2);
    }

    @Test
    void ne_divise_pas_par_zero_quand_deux_releves_tombent_le_meme_jour() {
        List<BodyCheckIn> series = List.of(checkIn(START, 80.0), checkIn(START, 79.5));

        BodyProgressCalculator.Indicators indicators = calculator.calculate(series, HEIGHT_CM);

        assertThat(indicators.averageWeeklyChangeKg()).isNull();
        assertThat(indicators.trend()).isEqualTo(WeightTrend.NOT_ENOUGH_DATA);
        assertThat(indicators.changeSincePreviousKg()).isEqualTo(-0.5);
    }

    @Test
    void calcule_l_imc_et_sa_categorie() {
        BodyProgressCalculator.Indicators indicators =
                calculator.calculate(List.of(checkIn(START, 72.5)), HEIGHT_CM);

        // 72,5 / 1,78^2 = 22,88 -> 22,9
        assertThat(indicators.currentBmi()).isEqualTo(22.9);
        assertThat(indicators.bmiCategory()).isEqualTo(BmiCategory.NORMAL);
    }

    @Test
    void n_invente_pas_d_imc_sans_taille() {
        BodyProgressCalculator.Indicators indicators =
                calculator.calculate(List.of(checkIn(START, 72.5)), null);

        assertThat(indicators.currentBmi()).isNull();
        assertThat(indicators.bmiCategory()).isNull();
    }

    @Test
    void classe_l_imc_selon_les_seuils_de_l_oms() {
        assertThat(BmiCategory.of(17.0)).isEqualTo(BmiCategory.UNDERWEIGHT);
        assertThat(BmiCategory.of(18.5)).isEqualTo(BmiCategory.NORMAL);
        assertThat(BmiCategory.of(24.9)).isEqualTo(BmiCategory.NORMAL);
        assertThat(BmiCategory.of(25.0)).isEqualTo(BmiCategory.OVERWEIGHT);
        assertThat(BmiCategory.of(30.0)).isEqualTo(BmiCategory.OBESE);
    }

    private BodyCheckIn checkIn(LocalDate date, double weightKg) {
        BodyCheckIn checkIn = new BodyCheckIn(null, date, Instant.now());
        checkIn.update(weightKg, null, null, null, null, null, null);
        return checkIn;
    }
}
