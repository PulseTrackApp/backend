package com.pulsetrack.backend.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.stats.dto.PersonalRecords;
import com.pulsetrack.backend.stats.dto.SportBreakdown;
import com.pulsetrack.backend.stats.dto.StatsBucket;
import com.pulsetrack.backend.stats.dto.StatsTotals;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de l'agregation statistique, sans Spring ni base.
 */
class StatsAggregatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId OUAGA = ZoneId.of("Africa/Ouagadougou");

    private final StatsAggregator aggregator = new StatsAggregator();

    @Test
    void additionne_les_totaux_et_compte_les_jours_actifs() {
        List<WorkoutStatsRow> rows = List.of(
                run("2026-08-03T06:00:00Z", 5_000, 1_800, 400),
                // Deuxieme sortie le meme jour : 2 seances, mais 1 seul jour actif.
                run("2026-08-03T18:00:00Z", 3_000, 1_200, 250),
                run("2026-08-05T06:00:00Z", 10_000, 3_600, 800));

        StatsTotals totals = aggregator.totalsOf(rows, UTC);

        assertThat(totals.sessionCount()).isEqualTo(3);
        assertThat(totals.activeDays()).isEqualTo(2);
        assertThat(totals.distanceMeters()).isEqualTo(18_000d);
        assertThat(totals.movingDurationSeconds()).isEqualTo(6_600L);
        assertThat(totals.caloriesBurned()).isEqualTo(1_450);
    }

    @Test
    void renvoie_des_totaux_vides_sans_aucune_seance() {
        StatsTotals totals = aggregator.totalsOf(List.of(), UTC);

        assertThat(totals.sessionCount()).isZero();
        assertThat(totals.distanceMeters()).isZero();
        assertThat(totals.activeDays()).isZero();
    }

    @Test
    void repartit_par_sport_du_plus_parcouru_au_moins_parcouru() {
        List<WorkoutStatsRow> rows = List.of(
                row("2026-08-03T06:00:00Z", SportType.WALK, 2_000, 1_800, 150),
                row("2026-08-04T06:00:00Z", SportType.RIDE, 30_000, 3_600, 700),
                row("2026-08-05T06:00:00Z", SportType.RUN, 8_000, 2_400, 600));

        List<SportBreakdown> breakdown = aggregator.breakdownBySport(rows, UTC);

        assertThat(breakdown).hasSize(3);
        assertThat(breakdown.get(0).sport()).isEqualTo(SportType.RIDE);
        assertThat(breakdown.get(0).distanceSharePercent()).isEqualTo(75.0);
        assertThat(breakdown.get(1).sport()).isEqualTo(SportType.RUN);
        assertThat(breakdown.get(2).sport()).isEqualTo(SportType.WALK);
    }

    @Test
    void n_invente_pas_de_part_quand_aucune_distance_n_a_ete_parcourue() {
        List<WorkoutStatsRow> rows = List.of(
                row("2026-08-03T06:00:00Z", SportType.OTHER, 0, 1_800, 150));

        assertThat(aggregator.breakdownBySport(rows, UTC).get(0).distanceSharePercent()).isNull();
    }

    @Test
    void produit_une_serie_continue_avec_les_jours_vides() {
        List<WorkoutStatsRow> rows = List.of(
                run("2026-08-03T06:00:00Z", 5_000, 1_800, 400),
                run("2026-08-06T06:00:00Z", 7_000, 2_400, 550));

        List<StatsBucket> series = aggregator.seriesOf(rows,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9),
                StatsPeriod.BucketSize.DAY, UTC);

        // Une courbe qui saute les jours sans sport laisserait croire a une
        // activite ininterrompue : les 7 jours doivent etre presents.
        assertThat(series).hasSize(7);
        assertThat(series.get(0).totals().distanceMeters()).isEqualTo(5_000d);
        assertThat(series.get(1).totals().sessionCount()).isZero();
        assertThat(series.get(3).totals().distanceMeters()).isEqualTo(7_000d);
        assertThat(series.get(0).label()).isEqualTo("03/08");
    }

    @Test
    void regroupe_par_mois_sur_une_annee() {
        List<WorkoutStatsRow> rows = List.of(
                run("2026-01-15T06:00:00Z", 5_000, 1_800, 400),
                run("2026-01-20T06:00:00Z", 5_000, 1_800, 400),
                run("2026-03-10T06:00:00Z", 8_000, 2_400, 600));

        List<StatsBucket> series = aggregator.seriesOf(rows,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                StatsPeriod.BucketSize.MONTH, UTC);

        assertThat(series).hasSize(12);
        assertThat(series.get(0).totals().sessionCount()).isEqualTo(2);
        assertThat(series.get(1).totals().sessionCount()).isZero();
        assertThat(series.get(2).totals().distanceMeters()).isEqualTo(8_000d);
    }

    @Test
    void rattache_les_seances_au_jour_du_fuseau_de_l_utilisateur() {
        // 23h30 UTC le 3 aout = 23h30 le 3 aout a Ouagadougou (UTC+0),
        // mais 01h30 le 4 aout a Paris (UTC+2).
        List<WorkoutStatsRow> rows = List.of(run("2026-08-03T23:30:00Z", 5_000, 1_800, 400));

        assertThat(aggregator.totalsOf(rows, OUAGA).activeDays()).isEqualTo(1);

        List<StatsBucket> paris = aggregator.seriesOf(rows,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5),
                StatsPeriod.BucketSize.DAY, ZoneId.of("Europe/Paris"));

        // A Paris la seance tombe le 4, pas le 3.
        assertThat(paris.get(0).totals().sessionCount()).isZero();
        assertThat(paris.get(1).totals().sessionCount()).isEqualTo(1);
    }

    @Test
    void identifie_les_records_de_la_periode() {
        List<WorkoutStatsRow> rows = List.of(
                new WorkoutStatsRow(Instant.parse("2026-08-03T06:00:00Z"), SportType.RUN,
                        5_000, 1_800, 400, 20, 360),
                new WorkoutStatsRow(Instant.parse("2026-08-03T18:00:00Z"), SportType.RUN,
                        4_000, 1_500, 320, 10, 375),
                new WorkoutStatsRow(Instant.parse("2026-08-05T06:00:00Z"), SportType.RUN,
                        12_000, 4_200, 950, 80, 350));

        PersonalRecords records = aggregator.recordsOf(rows, UTC);

        assertThat(records.bestPaceSecondsPerKm()).isEqualTo(350);
        assertThat(records.longestDistanceMeters()).isEqualTo(12_000d);
        assertThat(records.longestMovingDurationSeconds()).isEqualTo(4_200L);
        // Le 3 aout cumule 9 km sur deux sorties, contre 12 km le 5.
        assertThat(records.bestDayDistanceMeters()).isEqualTo(12_000d);
        assertThat(records.bestDay()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    void ne_renvoie_aucun_record_sans_seance() {
        PersonalRecords records = aggregator.recordsOf(List.of(), UTC);

        assertThat(records.bestPaceSecondsPerKm()).isNull();
        assertThat(records.longestDistanceMeters()).isNull();
        assertThat(records.bestDay()).isNull();
    }

    @Test
    void ignore_les_seances_sans_allure_pour_le_record_d_allure() {
        // Une seance sans distance n'a pas d'allure : elle ne doit pas devenir
        // un record a 0 seconde par kilometre.
        List<WorkoutStatsRow> rows = List.of(
                new WorkoutStatsRow(Instant.parse("2026-08-03T06:00:00Z"), SportType.OTHER,
                        0, 1_800, 200, 0, null),
                new WorkoutStatsRow(Instant.parse("2026-08-04T06:00:00Z"), SportType.RUN,
                        5_000, 1_800, 400, 0, 360));

        assertThat(aggregator.recordsOf(rows, UTC).bestPaceSecondsPerKm()).isEqualTo(360);
    }

    private WorkoutStatsRow run(String startedAt, double meters, long seconds, int calories) {
        return row(startedAt, SportType.RUN, meters, seconds, calories);
    }

    private WorkoutStatsRow row(String startedAt, SportType sport, double meters,
                                long seconds, int calories) {
        return new WorkoutStatsRow(Instant.parse(startedAt), sport, meters, seconds, calories, 0, null);
    }
}
