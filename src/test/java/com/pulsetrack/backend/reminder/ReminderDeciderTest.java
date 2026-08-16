package com.pulsetrack.backend.reminder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des regles de declenchement des rappels, sans Spring ni attente d'un
 * dimanche soir.
 */
class ReminderDeciderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

    private final ReminderDecider decider = new ReminderDecider();

    @Test
    void rappelle_la_pesee_a_qui_ne_s_est_jamais_pese() {
        assertThat(decider.shouldRemindCheckIn(Optional.empty(), TODAY)).isTrue();
    }

    @Test
    void rappelle_la_pesee_apres_plus_d_une_semaine() {
        assertThat(decider.shouldRemindCheckIn(Optional.of(TODAY.minusDays(8)), TODAY)).isTrue();
    }

    @Test
    void ne_rappelle_pas_quelqu_un_qui_s_est_pese_hier() {
        assertThat(decider.shouldRemindCheckIn(Optional.of(TODAY.minusDays(1)), TODAY)).isFalse();
    }

    @Test
    void ne_rappelle_pas_exactement_au_sixieme_jour() {
        // Le seuil est strict : a 6 jours on laisse encore la journee se finir.
        assertThat(decider.shouldRemindCheckIn(Optional.of(TODAY.minusDays(6)), TODAY)).isFalse();
        assertThat(decider.shouldRemindCheckIn(Optional.of(TODAY.minusDays(7)), TODAY)).isTrue();
    }

    @Test
    void n_alerte_pas_quand_aucun_objectif_n_est_fixe() {
        assertThat(decider.effortWarningMessage(List.of())).isEmpty();
    }

    @Test
    void n_alerte_pas_quand_les_objectifs_sont_bien_engages() {
        List<GoalProgressResponse> goals = List.of(
                goal(GoalType.WEEKLY_DISTANCE, "km", 20, 16, 80.0, false));

        assertThat(decider.effortWarningMessage(goals)).isEmpty();
    }

    @Test
    void n_alerte_pas_sur_un_objectif_deja_atteint() {
        List<GoalProgressResponse> goals = List.of(
                goal(GoalType.WEEKLY_SESSIONS, "seances", 3, 3, 100.0, true));

        assertThat(decider.effortWarningMessage(goals)).isEmpty();
    }

    @Test
    void alerte_quand_un_objectif_est_loin_du_compte() {
        List<GoalProgressResponse> goals = List.of(
                goal(GoalType.WEEKLY_DISTANCE, "km", 20, 6, 30.0, false));

        assertThat(decider.effortWarningMessage(goals))
                .contains("Il te reste 14 km à faire pour tenir ton objectif de la semaine.");
    }

    @Test
    void ne_cite_que_l_objectif_le_plus_en_retard() {
        // Une notification qui enumere tous les manques se fait ignorer.
        List<GoalProgressResponse> goals = List.of(
                goal(GoalType.WEEKLY_DISTANCE, "km", 20, 10, 50.0, false),
                goal(GoalType.WEEKLY_SESSIONS, "séances", 4, 1, 25.0, false));

        assertThat(decider.effortWarningMessage(goals))
                .contains("Il te reste 3 séances à faire pour tenir ton objectif de la semaine.");
    }

    @Test
    void ignore_un_objectif_dont_la_progression_est_incalculable() {
        // Poids cible sans aucune pesee : on ne peut rien affirmer.
        List<GoalProgressResponse> goals = List.of(new GoalProgressResponse(
                UUID.randomUUID(), GoalType.TARGET_WEIGHT, "kg", 75, null, null, null, false,
                null, false, null, null));

        assertThat(decider.effortWarningMessage(goals)).isEmpty();
    }

    private GoalProgressResponse goal(GoalType type, String unit, double target, double current,
                                      double completion, boolean achieved) {
        return new GoalProgressResponse(UUID.randomUUID(), type, unit, target, current,
                completion, Math.max(0, target - current), achieved,
                100d, achieved, current, null);
    }
}
