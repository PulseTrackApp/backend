package com.pulsetrack.backend.rating;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.rating.dto.RatingResponse;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le bareme de la note.
 *
 * <p>Le point le plus important n'est pas la formule mais son plancher : un
 * compte sans seance ne recoit pas zero. Noter zero quelqu'un qui vient
 * d'arriver est le plus sur moyen de le perdre.
 */
class RatingCalculatorTest {

    private final RatingCalculator calculator = new RatingCalculator();

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final ZoneId ZONE = ZoneOffset.UTC;

    @Test
    void accueille_un_compte_sans_aucune_seance_au_lieu_de_le_noter_zero() {
        RatingResponse rating = calculator.rate(
                new RatingCalculator.Inputs(List.of(), List.of(), 0, List.of(), 0),
                null, TODAY, ZONE);

        assertThat(rating.tier()).isEqualTo(RatingTier.NEW);
        assertThat(rating.score()).isNull();
        assertThat(rating.grade()).isNull();
        assertThat(rating.message()).contains("La note apparaitra des la premiere");
        assertThat(rating.components()).isEmpty();
    }

    @Test
    void recompense_la_regularite_plus_que_le_volume() {
        // Meme volume total, reparti sur seize jours d'un cote et sur quatre de
        // l'autre. C'est la regularite qui doit faire la difference.
        RatingResponse spread = calculator.rate(inputs(sessions(16, 2_250), 16), null, TODAY, ZONE);
        RatingResponse crammed = calculator.rate(inputs(sessions(4, 9_000), 4), null, TODAY, ZONE);

        assertThat(spread.score()).isGreaterThan(crammed.score());
    }

    @Test
    void atteint_la_note_pleine_de_regularite_a_seize_jours_actifs() {
        RatingResponse rating = calculator.rate(inputs(sessions(16, 2_250), 16), null, TODAY, ZONE);

        assertThat(componentOf(rating, RatingComponent.REGULARITY).score()).isEqualTo(100);
    }

    @Test
    void retire_la_composante_objectifs_quand_il_n_y_en_a_aucun() {
        RatingResponse rating = calculator.rate(inputs(sessions(8, 2_000), 8), null, TODAY, ZONE);

        assertThat(rating.components()).extracting(RatingResponse.Component::key)
                .doesNotContain(RatingComponent.GOALS);
        // Les poids restants sont renormalises : sans cela la note serait
        // plafonnee a 75 sans qu'aucun ecran ne dise pourquoi.
        assertThat(rating.score()).isNotNull();
    }

    @Test
    void ne_plafonne_pas_la_note_faute_d_objectif() {
        // Regularite, volume et progression au maximum, sans objectif fixe.
        List<WorkoutStatsRow> window = sessions(28, 3_600);
        List<WorkoutStatsRow> previous = sessions(28, 1_000);

        RatingResponse rating = calculator.rate(
                new RatingCalculator.Inputs(window, previous, 28, List.of(), 28), null, TODAY, ZONE);

        assertThat(rating.score()).isEqualTo(100);
    }

    @Test
    void note_les_objectifs_sur_les_quatre_dernieres_periodes_de_sept_jours() {
        // Une seance de 10 km par jour, objectif de 20 km par semaine : tenu les
        // quatre fois.
        List<Goal> goals = List.of(goal(GoalType.WEEKLY_DISTANCE, 20));
        RatingResponse rating = calculator.rate(
                new RatingCalculator.Inputs(sessions(28, 3_600, 10_000), List.of(), 28, goals, 28),
                null, TODAY, ZONE);

        assertThat(componentOf(rating, RatingComponent.GOALS).score()).isEqualTo(100);
    }

    @Test
    void rend_une_progression_neutre_sans_periode_de_reference() {
        RatingResponse rating = calculator.rate(inputs(sessions(8, 2_000), 8), null, TODAY, ZONE);

        RatingResponse.Component progression = componentOf(rating, RatingComponent.PROGRESSION);
        // Ne pas avoir progresse faute de passe n'est pas un defaut.
        assertThat(progression.score()).isEqualTo(70);
        assertThat(progression.comment()).contains("rien a quoi se comparer");
    }

    @Test
    void distingue_hausse_stabilite_et_baisse_de_volume() {
        int stable = calculator.scoreOf(
                new RatingCalculator.Inputs(sessions(8, 2_000), sessions(8, 2_000), 8, List.of(), 0),
                TODAY, ZONE);
        int growing = calculator.scoreOf(
                new RatingCalculator.Inputs(sessions(8, 3_000), sessions(8, 2_000), 8, List.of(), 0),
                TODAY, ZONE);
        int shrinking = calculator.scoreOf(
                new RatingCalculator.Inputs(sessions(8, 1_000), sessions(8, 2_000), 8, List.of(), 0),
                TODAY, ZONE);

        assertThat(growing).isGreaterThan(stable);
        assertThat(stable).isGreaterThan(shrinking);
    }

    @Test
    void compare_la_note_a_celle_du_mois_precedent() {
        RatingResponse rating = calculator.rate(inputs(sessions(12, 2_400), 12), 50, TODAY, ZONE);

        assertThat(rating.trend().previousScore()).isEqualTo(50);
        assertThat(rating.trend().delta()).isEqualTo(rating.score() - 50);
        assertThat(rating.trend().direction()).isEqualTo(RatingResponse.Trend.Direction.UP);
    }

    @Test
    void annonce_le_palier_suivant_et_ce_qu_il_manque() {
        RatingResponse rating = calculator.rate(inputs(sessions(6, 1_800), 6), null, TODAY, ZONE);

        assertThat(rating.nextTier()).isNotNull();
        assertThat(rating.pointsToNextTier()).isNotNull();
        assertThat(rating.score() + rating.pointsToNextTier())
                .isGreaterThanOrEqualTo(rating.nextTier().minimumScore());
    }

    @Test
    void conseille_d_abord_la_composante_la_plus_faible() {
        // Beaucoup de volume concentre sur trois jours : c'est la regularite qui
        // manque, et c'est elle qu'il faut nommer.
        RatingResponse rating = calculator.rate(inputs(sessions(3, 12_000), 3), null, TODAY, ZONE);

        assertThat(rating.advice()).contains("une sortie de plus par semaine");
    }

    @Test
    void borne_la_note_entre_zero_et_cent() {
        RatingResponse huge = calculator.rate(inputs(sessions(28, 20_000), 28), null, TODAY, ZONE);
        RatingResponse tiny = calculator.rate(inputs(sessions(1, 60), 1), null, TODAY, ZONE);

        assertThat(huge.score()).isBetween(0, 100);
        assertThat(tiny.score()).isBetween(0, 100);
    }

    private RatingCalculator.Inputs inputs(List<WorkoutStatsRow> window, int activeDays) {
        return new RatingCalculator.Inputs(window, List.of(), activeDays, List.of(), 0);
    }

    private List<WorkoutStatsRow> sessions(int count, long movingSecondsEach) {
        return sessions(count, movingSecondsEach, 5_000);
    }

    /** Une seance par jour, en remontant depuis aujourd'hui. */
    private List<WorkoutStatsRow> sessions(int count, long movingSecondsEach, double metersEach) {
        List<WorkoutStatsRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Instant day = TODAY.minusDays(index).atStartOfDay(ZONE).toInstant().plusSeconds(3_600 * 7);
            rows.add(new WorkoutStatsRow(day, SportType.RUN, metersEach, movingSecondsEach, 400, 0, 300));
        }
        return rows;
    }

    private Goal goal(GoalType type, double target) {
        Goal goal = new Goal(UUID.randomUUID(), type, Instant.parse("2026-07-01T00:00:00Z"));
        goal.update(target, LocalDate.of(2026, 7, 1), null, Instant.parse("2026-07-01T00:00:00Z"));
        return goal;
    }

    private RatingResponse.Component componentOf(RatingResponse rating, RatingComponent key) {
        return rating.components().stream()
                .filter(one -> one.key() == key)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Composante absente : " + key));
    }
}
