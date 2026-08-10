package com.pulsetrack.backend.summary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la confrontation objectif / realise, sans Spring.
 */
class GoalProgressCalculatorTest {

    private final GoalProgressCalculator calculator = new GoalProgressCalculator();

    /** 3 seances, 12 km, 75 minutes en mouvement, 900 kcal. */
    private static final WorkoutTotals.Normalized WEEK =
            new WorkoutTotals.Normalized(3, 12_000d, 4_500L, 900, 120d);

    @Test
    void mesure_un_objectif_de_distance_hebdomadaire() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null);

        assertThat(progress.unit()).isEqualTo("km");
        assertThat(progress.currentValue()).isEqualTo(12.0);
        assertThat(progress.completionPercent()).isEqualTo(60.0);
        assertThat(progress.remaining()).isEqualTo(8.0);
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void mesure_un_objectif_en_nombre_de_seances() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_SESSIONS, 4), WEEK, null, null);

        assertThat(progress.currentValue()).isEqualTo(3.0);
        assertThat(progress.remaining()).isEqualTo(1.0);
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void convertit_les_secondes_en_minutes_pour_un_objectif_de_duree() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DURATION, 60), WEEK, null, null);

        // 4 500 s = 75 min, soit 125 % d'un objectif de 60 min
        assertThat(progress.currentValue()).isEqualTo(75.0);
        assertThat(progress.completionPercent()).isEqualTo(125.0);
        assertThat(progress.remaining()).isZero();
        assertThat(progress.achieved()).isTrue();
    }

    @Test
    void ne_plafonne_pas_le_pourcentage_a_cent() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_CALORIES, 600), WEEK, null, null);

        // Depasser son objectif merite d'etre affiche tel quel.
        assertThat(progress.completionPercent()).isEqualTo(150.0);
    }

    @Test
    void mesure_la_progression_vers_un_poids_cible_a_perdre() {
        // Depart 80 kg, cible 75 kg, actuellement 78 kg : 2 kg sur 5, soit 40 %.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, 80.0, 78.0);

        assertThat(progress.unit()).isEqualTo("kg");
        assertThat(progress.currentValue()).isEqualTo(78.0);
        assertThat(progress.completionPercent()).isEqualTo(40.0);
        assertThat(progress.remaining()).isEqualTo(3.0);
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void marque_atteint_un_poids_cible_franchi() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, 80.0, 74.5);

        assertThat(progress.achieved()).isTrue();
        assertThat(progress.completionPercent()).isEqualTo(110.0);
    }

    @Test
    void gere_un_poids_cible_a_prendre() {
        // Depart 60 kg, cible 65 kg, actuellement 62 kg : 2 kg sur 5, soit 40 %.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 65), WEEK, 60.0, 62.0);

        assertThat(progress.completionPercent()).isEqualTo(40.0);
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void n_invente_pas_de_pourcentage_sans_aucune_pesee() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, null, null);

        assertThat(progress.currentValue()).isNull();
        assertThat(progress.completionPercent()).isNull();
        assertThat(progress.achieved()).isFalse();
    }

    @Test
    void ne_renvoie_pas_de_pourcentage_negatif_si_le_poids_s_eloigne() {
        // Depart 80 kg, cible 75, mais on est remonte a 82 : progression 0, pas -40 %.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, 80.0, 82.0);

        assertThat(progress.completionPercent()).isZero();
        assertThat(progress.remaining()).isEqualTo(7.0);
    }

    @Test
    void repond_zero_sur_une_semaine_sans_aucune_seance() {
        // Ce que renvoie reellement la base sans aucune ligne : les sum() valent
        // null, pas zero. orZero() est ce qui evite le NullPointerException.
        WorkoutTotals.Normalized empty = new WorkoutTotals(null, null, null, null, null).orZero();

        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), empty, null, null);

        assertThat(progress.currentValue()).isZero();
        assertThat(progress.remaining()).isEqualTo(20.0);
        assertThat(progress.achieved()).isFalse();
    }

    private Goal goal(GoalType type, double targetValue) {
        Goal goal = new Goal(UUID.randomUUID(), type, Instant.now());
        goal.update(targetValue, LocalDate.now(), null, Instant.now());
        return goal;
    }
}
