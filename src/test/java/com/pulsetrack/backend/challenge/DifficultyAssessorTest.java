package com.pulsetrack.backend.challenge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.achievement.SportPerformanceRow;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.common.domain.SportType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'appreciation rendue <em>avant</em> l'effort. Elle repond a « est-ce que je
 * vise juste ? », question que personne ne peut trancher seul.
 */
class DifficultyAssessorTest {

    private final DifficultyAssessor assessor = new DifficultyAssessor();

    @Test
    void ne_juge_pas_sans_historique_suffisant() {
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 300, history(360, 355));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.INCONNU);
        assertThat(difficulty.referenceBasis()).isEqualTo(DifficultyLevel.ReferenceBasis.NONE);
        assertThat(difficulty.referencePaceSecondsPerKm()).isNull();
    }

    @Test
    void declare_accessible_un_defi_plus_lent_que_l_habitude() {
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 400, history(360, 365, 355));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.ACCESSIBLE);
        assertThat(difficulty.referencePaceSecondsPerKm()).isEqualTo(360);
    }

    @Test
    void declare_realiste_un_defi_a_peine_plus_rapide() {
        // 3 % plus rapide que 360 s/km.
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 350, history(360, 360, 360));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.REALISTE);
    }

    @Test
    void declare_ambitieux_le_cran_qui_fait_progresser() {
        // 10 % plus rapide.
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 324, history(360, 360, 360));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.AMBITIEUX);
        assertThat(difficulty.referenceBasis()).isEqualTo(DifficultyLevel.ReferenceBasis.AVERAGE_LAST_10);
    }

    @Test
    void parle_de_retour_au_sommet_quand_l_allure_a_deja_ete_tenue() {
        // Tres au-dessus de la moyenne (350 s/km), mais deja realise une fois :
        // ce n'est pas hors de portee, c'est un retour au sommet.
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 290, history(360, 365, 370, 355, 360, 290));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.AMBITIEUX);
        assertThat(difficulty.referenceBasis()).isEqualTo(DifficultyLevel.ReferenceBasis.BEST_EVER);
        assertThat(difficulty.referencePaceSecondsPerKm()).isEqualTo(290);
    }

    @Test
    void avertit_sans_interdire_un_defi_hors_de_portee() {
        ChallengeResponse.Difficulty difficulty =
                assessor.assess(SportType.RUN, 200, history(360, 355, 365));

        assertThat(difficulty.level()).isEqualTo(DifficultyLevel.HORS_DE_PORTEE);
        // Le message propose un premier palier plutot que de refuser.
        assertThat(difficulty.message()).contains("Rien ne t'empeche d'essayer");
    }

    @Test
    void ecarte_les_seances_trop_courtes_de_la_reference() {
        List<SportPerformanceRow> rows = new ArrayList<>(history(360, 360, 360));
        // Un sprint de 300 metres a 3:00/km fausserait la moyenne du tout au tout.
        rows.add(new SportPerformanceRow(UUID.randomUUID(), Instant.parse("2026-08-14T06:00:00Z"),
                300, 54, 180, 0));

        ChallengeResponse.Difficulty difficulty = assessor.assess(SportType.RUN, 350, rows);

        assertThat(difficulty.referencePaceSecondsPerKm()).isEqualTo(360);
    }

    @Test
    void ne_retient_que_les_dix_seances_les_plus_recentes() {
        List<SportPerformanceRow> rows = new ArrayList<>();
        // Douze seances lentes, puis dix rapides plus recentes : la moyenne doit
        // refleter la forme actuelle, pas celle d'il y a six mois.
        for (int index = 0; index < 12; index++) {
            rows.add(row(Instant.parse("2026-01-01T06:00:00Z").plusSeconds(index * 86_400L), 480));
        }
        for (int index = 0; index < 10; index++) {
            rows.add(row(Instant.parse("2026-08-01T06:00:00Z").plusSeconds(index * 86_400L), 360));
        }

        ChallengeResponse.Difficulty difficulty = assessor.assess(SportType.RUN, 350, rows);

        assertThat(difficulty.referencePaceSecondsPerKm()).isEqualTo(360);
    }

    private List<SportPerformanceRow> history(int... paces) {
        List<SportPerformanceRow> rows = new ArrayList<>();
        Instant day = Instant.parse("2026-08-01T06:00:00Z");
        for (int pace : paces) {
            rows.add(row(day, pace));
            day = day.plusSeconds(86_400);
        }
        return rows;
    }

    private SportPerformanceRow row(Instant startedAt, int pace) {
        return new SportPerformanceRow(UUID.randomUUID(), startedAt, 5_000, 5L * pace, pace, 0);
    }
}
