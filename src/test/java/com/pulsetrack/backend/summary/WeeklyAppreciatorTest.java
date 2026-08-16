package com.pulsetrack.backend.summary;

import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.motivation.Appreciation;
import com.pulsetrack.backend.motivation.AppreciationTier;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'avis d'ensemble sur la semaine.
 *
 * <p>Ce qui compte autant que le verdict : <strong>aucun message n'accuse</strong>.
 * Celui qui a le plus besoin d'encouragement est precisement celui qui a le moins
 * couru, et une application de sport qui gronde se desinstalle.
 */
class WeeklyAppreciatorTest {

    private final WeeklyAppreciator appreciator = new WeeklyAppreciator();

    private static final WorkoutTotals.Normalized EMPTY = new WorkoutTotals.Normalized(0, 0d, 0L, 0, 0d);

    @Test
    void ne_s_inquiete_pas_d_une_semaine_vide_le_mardi_matin() {
        Appreciation appreciation = appreciator.appreciate(EMPTY, week(4, 20_000), List.of(), 0.2);

        assertThat(appreciation.tier()).isEqualTo(AppreciationTier.ON_TRACK);
        assertThat(appreciation.headline()).isEqualTo("La semaine commence");
    }

    @Test
    void signale_une_semaine_vide_arrivee_a_son_terme_sans_reprocher() {
        Appreciation appreciation = appreciator.appreciate(EMPTY, week(4, 20_000), List.of(), 0.95);

        assertThat(appreciation.tier()).isEqualTo(AppreciationTier.BEHIND);
        assertThat(appreciation.advice()).contains("Le plus dur est de sortir");
    }

    @Test
    void felicite_quand_tous_les_objectifs_sont_tenus() {
        Appreciation appreciation = appreciator.appreciate(
                week(4, 25_000), week(3, 18_000), List.of(goal(100, true, true)), 0.9);

        assertThat(appreciation.tier()).isEqualTo(AppreciationTier.EXCELLENT);
        assertThat(appreciation.headline()).isEqualTo("Tous les objectifs tenus");
    }

    @Test
    void ne_promet_pas_une_bonne_semaine_au_dessus_d_une_jauge_rouge() {
        // Le verdict d'ensemble ne peut pas etre meilleur que celui de l'objectif
        // le plus mal en point.
        Appreciation appreciation = appreciator.appreciate(
                week(1, 3_000), week(4, 25_000),
                List.of(goal(12, false, false, AppreciationTier.AT_RISK)), 0.9);

        assertThat(appreciation.tier()).isEqualTo(AppreciationTier.AT_RISK);
    }

    @Test
    void se_compare_a_la_semaine_precedente_faute_d_objectif() {
        Appreciation better = appreciator.appreciate(week(4, 25_000), week(3, 18_000), List.of(), 0.9);
        Appreciation worse = appreciator.appreciate(week(2, 9_000), week(4, 25_000), List.of(), 0.9);

        assertThat(better.tier()).isEqualTo(AppreciationTier.GOOD);
        assertThat(better.message()).contains("de plus que la semaine derniere");
        assertThat(worse.tier()).isEqualTo(AppreciationTier.BEHIND);
        assertThat(worse.message()).contains("de moins que la semaine derniere");
    }

    @Test
    void ne_reproche_rien_a_une_premiere_semaine() {
        // Rien a quoi se comparer, et rien de moins encourageant que de se voir
        // reprocher un point de depart.
        Appreciation appreciation = appreciator.appreciate(week(2, 8_000), EMPTY, List.of(), 0.9);

        assertThat(appreciation.tier()).isEqualTo(AppreciationTier.GOOD);
        assertThat(appreciation.advice()).contains("Fixe-toi un objectif");
    }

    @Test
    void ne_cite_qu_un_seul_objectif_en_retard() {
        Appreciation appreciation = appreciator.appreciate(
                week(2, 9_000), week(3, 18_000),
                List.of(goal(45, false, false), goal(80, false, true)), 0.9);

        // Un message qui enumere quatre manques se fait ignorer.
        assertThat(appreciation.advice()).contains("Il reste");
        assertThat(appreciation.advice().split("Il reste")).hasSize(2);
    }

    private WorkoutTotals.Normalized week(int sessions, double meters) {
        return new WorkoutTotals.Normalized(sessions, meters, sessions * 1_800L, sessions * 300, 0d);
    }

    private GoalProgressResponse goal(double completion, boolean achieved, boolean onTrack) {
        return goal(completion, achieved, onTrack, AppreciationTier.BEHIND);
    }

    private GoalProgressResponse goal(double completion,
                                      boolean achieved,
                                      boolean onTrack,
                                      AppreciationTier tier) {
        return new GoalProgressResponse(UUID.randomUUID(), GoalType.WEEKLY_DISTANCE, "km",
                20.0, completion / 5, completion, Math.max(0, 20 - completion / 5), achieved,
                90d, onTrack, null,
                new Appreciation(achieved ? AppreciationTier.EXCELLENT : tier, "titre", "message", null));
    }
}
