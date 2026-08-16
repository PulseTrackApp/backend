package com.pulsetrack.backend.summary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.motivation.AppreciationTier;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.WorkoutTotals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'appreciation portee sur un objectif.
 *
 * <p>Tout le sujet tient en une phrase : <strong>le pourcentage seul ne dit
 * rien</strong>. Quarante pour cent d'un objectif hebdomadaire, c'est de
 * l'avance le mardi et du retard le samedi. C'est cette comparaison au temps
 * ecoule qui manquait, et qu'aucun client ne peut faire seul sans connaitre le
 * fuseau de l'utilisateur.
 */
class GoalAppreciationTest {

    private final GoalProgressCalculator calculator = new GoalProgressCalculator();

    /** 3 seances, 12 km, 75 minutes en mouvement, 900 kcal. */
    private static final WorkoutTotals.Normalized WEEK =
            new WorkoutTotals.Normalized(3, 12_000d, 4_500L, 900, 120d);

    @Test
    void juge_en_avance_le_meme_chiffre_en_debut_de_semaine() {
        // 60 % de l'objectif alors qu'un tiers de la semaine s'est ecoule.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, 0.33);

        assertThat(progress.completionPercent()).isEqualTo(60.0);
        assertThat(progress.onTrack()).isTrue();
        assertThat(progress.appreciation().tier()).isEqualTo(AppreciationTier.GOOD);
    }

    @Test
    void juge_en_retard_le_meme_chiffre_en_fin_de_semaine() {
        // Exactement le meme avancement, mais samedi soir.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, 0.90);

        assertThat(progress.completionPercent()).isEqualTo(60.0);
        assertThat(progress.onTrack()).isFalse();
        assertThat(progress.appreciation().tier()).isEqualTo(AppreciationTier.AT_RISK);
    }

    @Test
    void tolere_un_leger_retard_sans_faire_clignoter_l_ecran() {
        // 60 % a 63 % de semaine ecoulee : trois points de retard, ce n'est pas
        // une alerte.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, 0.63);

        assertThat(progress.onTrack()).isTrue();
        assertThat(progress.appreciation().tier()).isEqualTo(AppreciationTier.ON_TRACK);
    }

    @Test
    void projette_la_fin_de_semaine_au_rythme_actuel() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 25), WEEK, null, null, 0.50);

        // 12 km a la moitie de la semaine : 24 km au terme.
        assertThat(progress.projectedValue()).isEqualTo(24.0);
        assertThat(progress.appreciation().advice()).contains("il manquerait 1 km");
    }

    @Test
    void ne_projette_rien_en_tout_debut_de_semaine() {
        // Deduire une semaine entiere de trois heures d'activite donnerait un
        // chiffre fantaisiste que l'utilisateur prendrait au serieux.
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, 0.02);

        assertThat(progress.projectedValue()).isNull();
        assertThat(progress.appreciation().advice()).isNull();
    }

    @Test
    void declare_l_objectif_atteint_quel_que_soit_le_moment() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 10), WEEK, null, null, 0.20);

        assertThat(progress.achieved()).isTrue();
        assertThat(progress.onTrack()).isTrue();
        assertThat(progress.appreciation().tier()).isEqualTo(AppreciationTier.EXCELLENT);
        assertThat(progress.appreciation().advice()).contains("prochain palier");
    }

    @Test
    void ne_donne_ni_temps_ecoule_ni_projection_a_un_objectif_de_poids() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, 80.0, 78.0, 0.5);

        assertThat(progress.elapsedPercent()).isNull();
        assertThat(progress.projectedValue()).isNull();
        assertThat(progress.appreciation()).isNotNull();
    }

    @Test
    void accueille_un_objectif_de_poids_sans_pesee() {
        GoalProgressResponse progress = calculator.progressOf(
                goal(GoalType.TARGET_WEIGHT, 75), WEEK, null, null, 0.5);

        assertThat(progress.appreciation().tier()).isEqualTo(AppreciationTier.NO_DATA);
        assertThat(progress.appreciation().message()).contains("Note ton poids");
    }

    @Test
    void ramene_une_fraction_de_semaine_aberrante_dans_ses_bornes() {
        // Une horloge deraillee ne doit pas produire un pourcentage negatif ni
        // un pourcentage a trois chiffres.
        GoalProgressResponse under = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, -3);
        GoalProgressResponse over = calculator.progressOf(
                goal(GoalType.WEEKLY_DISTANCE, 20), WEEK, null, null, 42);

        assertThat(under.elapsedPercent()).isZero();
        assertThat(over.elapsedPercent()).isEqualTo(100.0);
    }

    private Goal goal(GoalType type, double target) {
        Goal goal = new Goal(UUID.randomUUID(), type, Instant.parse("2026-08-10T00:00:00Z"));
        goal.update(target, LocalDate.of(2026, 8, 10), null, Instant.parse("2026-08-10T00:00:00Z"));
        return goal;
    }
}
