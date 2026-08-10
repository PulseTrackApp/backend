package com.pulsetrack.backend.coach;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BmiCategory;
import com.pulsetrack.backend.bodycheckin.WeightTrend;
import com.pulsetrack.backend.bodycheckin.dto.BodyProgressResponse;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.profile.FitnessLevel;
import com.pulsetrack.backend.profile.PrimaryGoal;
import com.pulsetrack.backend.profile.Sex;
import com.pulsetrack.backend.profile.dto.ProfileResponse;
import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;
import com.pulsetrack.backend.workout.Feeling;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des textes envoyes a Gemini.
 *
 * <p>Les garde-fous de la spec produit sont une exigence fonctionnelle : les
 * verifier ici evite qu'une reecriture du prompt ne les fasse disparaitre sans
 * que personne ne s'en apercoive.
 */
class CoachPromptBuilderTest {

    private final CoachPromptBuilder builder = new CoachPromptBuilder();

    @Test
    void l_instruction_systeme_interdit_le_diagnostic_medical() {
        String instruction = builder.systemInstruction(CoachingTone.ENCOURAGING);

        assertThat(instruction)
                .contains("Tu n'es pas medecin")
                .contains("aucun diagnostic")
                .contains("consulter un professionnel de sante")
                .contains("aucun regime alimentaire");
    }

    @Test
    void l_instruction_systeme_interdit_l_effort_disproportionne() {
        String instruction = builder.systemInstruction(CoachingTone.DEMANDING);

        assertThat(instruction)
                .contains("jamais un effort disproportionne")
                .contains("progression de volume reste progressive");
    }

    @Test
    void l_instruction_systeme_laisse_l_utilisateur_libre_de_refuser() {
        String instruction = builder.systemInstruction(CoachingTone.FACTUAL);

        assertThat(instruction)
                .contains("libre de les ignorer")
                .contains("ne culpabilises jamais");
    }

    @Test
    void le_ton_choisi_est_repercute_dans_l_instruction() {
        assertThat(builder.systemInstruction(CoachingTone.ENCOURAGING))
                .contains("bienveillant et encourageant");
        assertThat(builder.systemInstruction(CoachingTone.DEMANDING))
                .contains("exigeant et oriente performance");
    }

    @Test
    void le_bilan_hebdo_transmet_les_chiffres_de_la_semaine() {
        String prompt = builder.weeklyReviewPrompt(context(withCheckIns(), sessions()));

        assertThat(prompt)
                .contains("Seances : 2")
                .contains("12.0 km")
                .contains("900 kcal")
                .contains("Jours consecutifs avec activite : 3")
                // L'objectif et son avancement doivent y figurer, sans quoi le
                // modele ne peut pas juger si l'effort est suffisant.
                .contains("WEEKLY_DISTANCE : 12.0 / 20.0 km (60.0 %)");
    }

    @Test
    void le_bilan_hebdo_transmet_l_evolution_physique() {
        String prompt = builder.weeklyReviewPrompt(context(withCheckIns(), sessions()));

        assertThat(prompt)
                .contains("Poids actuel : 78.0 kg")
                .contains("Variation totale : -2.0 kg")
                .contains("Tendance : LOSING");
    }

    @Test
    void interdit_explicitement_d_inventer_une_tendance_sans_pesee() {
        String prompt = builder.weeklyReviewPrompt(context(withoutCheckIns(), sessions()));

        assertThat(prompt).contains("N'invente aucune tendance de poids");
        assertThat(prompt).doesNotContain("Tendance : ");
    }

    @Test
    void formate_l_allure_en_minutes_par_kilometre() {
        String prompt = builder.weeklyReviewPrompt(context(withCheckIns(), sessions()));

        // 330 s/km doit se lire 5:30/km, pas 330
        assertThat(prompt).contains("allure 5:30/km");
    }

    @Test
    void la_question_libre_reste_encadree_et_neutralise_les_guillemets() {
        String prompt = builder.freeQuestionPrompt(
                context(withCheckIns(), sessions()),
                "Dois-je \"forcer\" demain ?");

        assertThat(prompt)
                .contains("sans sortir de ton role de coach sportif")
                // Les guillemets de la question ne doivent pas fermer le
                // delimiteur qui l'entoure.
                .contains("Dois-je 'forcer' demain ?")
                .doesNotContain("\"forcer\"");
    }

    @Test
    void supporte_une_absence_totale_de_seance() {
        CoachContext empty = new CoachContext(profile(), week(List.of()), withoutCheckIns(), List.of());

        assertThat(builder.weeklyReviewPrompt(empty)).contains("Aucune seance enregistree.");
    }

    // --- Fabriques de contexte ----------------------------------------------

    private CoachContext context(BodyProgressResponse body, List<WorkoutSummaryResponse> sessions) {
        return new CoachContext(profile(), week(goals()), body, sessions);
    }

    private ProfileResponse profile() {
        return new ProfileResponse(UUID.randomUUID(), "Nicolas", 178, 78.0,
                LocalDate.of(1995, 4, 12), 31, Sex.MALE, PrimaryGoal.IMPROVE_ENDURANCE,
                FitnessLevel.INTERMEDIATE, Set.of(SportType.RUN), 24.6,
                Instant.now(), Instant.now());
    }

    private List<GoalProgressResponse> goals() {
        return List.of(new GoalProgressResponse(UUID.randomUUID(), GoalType.WEEKLY_DISTANCE,
                "km", 20.0, 12.0, 60.0, 8.0, false));
    }

    private WeeklySummaryResponse week(List<GoalProgressResponse> goals) {
        return new WeeklySummaryResponse(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9), "UTC",
                2, 12_000d, 4_500L, 900, 120d,
                new WeeklySummaryResponse.WeeklyComparison(1, 3_000d, 900L, 200, 33.3),
                goals, 3);
    }

    private BodyProgressResponse withCheckIns() {
        return new BodyProgressResponse(List.of(), 3, 80.0, 78.0, -2.0, -1.0, -0.5,
                WeightTrend.LOSING, 24.6, BmiCategory.NORMAL);
    }

    private BodyProgressResponse withoutCheckIns() {
        return new BodyProgressResponse(List.of(), 0, null, null, null, null, null,
                WeightTrend.NOT_ENOUGH_DATA, null, null);
    }

    private List<WorkoutSummaryResponse> sessions() {
        return List.of(new WorkoutSummaryResponse(
                UUID.randomUUID(), SportType.RUN,
                Instant.parse("2026-08-08T06:00:00Z"), Instant.parse("2026-08-08T06:33:00Z"),
                1980L, 1980L, 6_000d, 330, 10.9, 12.5, 60d, 480,
                6, Feeling.GOOD, null));
    }
}
