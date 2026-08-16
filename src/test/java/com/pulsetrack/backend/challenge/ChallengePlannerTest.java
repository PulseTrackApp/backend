package com.pulsetrack.backend.challenge;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.CueKind;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.CueTrigger;
import com.pulsetrack.backend.common.domain.SportType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le plan est ce qui rend les alertes d'echeance possibles hors ligne. Ces tests
 * verifient qu'il est complet et lisible sans reseau.
 */
class ChallengePlannerTest {

    private final ChallengePlanner planner = new ChallengePlanner();

    @Test
    void jalonne_un_dix_kilometres_au_kilometre() {
        ChallengePlanResponse plan = planner.planFor(challenge(10_000, 3_300));

        // Neuf jalons : la cible n'en est pas un, c'est l'arrivee.
        assertThat(plan.splits()).hasSize(9);
        assertThat(plan.splits().get(0).label()).isEqualTo("km 1");
        assertThat(plan.splits().get(0).targetElapsedSeconds()).isEqualTo(330);
        assertThat(plan.splits().get(8).distanceMeters()).isEqualTo(9_000d);
    }

    @Test
    void jalonne_un_parcours_court_au_demi_kilometre() {
        ChallengePlanResponse plan = planner.planFor(challenge(2_000, 600));

        assertThat(plan.splits()).hasSize(3);
        assertThat(plan.splits().get(0).label()).isEqualTo("500 m");
    }

    @Test
    void borne_le_nombre_de_jalons_sur_une_tres_longue_distance() {
        ChallengePlanResponse plan = planner.planFor(challenge(100_000, 36_000));

        // Cent lignes seraient un mur de chiffres que personne ne lit.
        assertThat(plan.splits()).hasSizeLessThanOrEqualTo(ChallengePlanner.MAX_SPLITS);
    }

    @Test
    void remet_les_alertes_d_echeance_avec_leurs_messages() {
        ChallengePlanResponse plan = planner.planFor(challenge(10_000, 3_300));

        assertThat(plan.cues())
                .filteredOn(cue -> cue.kind() == CueKind.DEADLINE_ALERT)
                .extracting(ChallengePlanResponse.Cue::threshold)
                .containsExactlyInAnyOrder(300d, 60d);

        assertThat(plan.cues()).allSatisfy(cue -> {
            assertThat(cue.message()).isNotBlank();
            assertThat(cue.title()).isNotBlank();
        });
    }

    @Test
    void n_annonce_pas_cinq_minutes_restantes_sur_un_defi_de_huit_minutes() {
        // L'alerte tomberait avant meme la moitie de l'effort et inquieterait
        // pour rien.
        ChallengePlanResponse plan = planner.planFor(challenge(2_000, 480));

        assertThat(plan.cues())
                .filteredOn(cue -> cue.trigger() == CueTrigger.REMAINING_SECONDS)
                .extracting(ChallengePlanResponse.Cue::threshold)
                .containsExactly(60d);
    }

    @Test
    void n_annonce_pas_les_cinq_cents_derniers_metres_sur_un_kilometre() {
        ChallengePlanResponse plan = planner.planFor(challenge(1_000, 300));

        assertThat(plan.cues())
                .noneMatch(cue -> cue.trigger() == CueTrigger.DISTANCE_REMAINING_METERS);
    }

    @Test
    void rappelle_l_allure_a_tenir() {
        ChallengePlanResponse plan = planner.planFor(challenge(10_000, 3_300));

        assertThat(plan.requiredPaceSecondsPerKm()).isEqualTo(330);
        assertThat(plan.requiredSpeedKmh()).isEqualTo(10.9);
    }

    private Challenge challenge(double distanceMeters, long durationSeconds) {
        return new Challenge(UUID.randomUUID(), UUID.randomUUID(), "defi",
                SportType.RUN, distanceMeters, durationSeconds, null, null,
                Instant.parse("2026-08-15T06:00:00Z"));
    }
}
