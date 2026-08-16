package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le coeur du sujet : quand faut-il feliciter, et surtout quand faut-il se taire.
 *
 * <p>Les marges anti-bruit sont la raison d'etre de ces tests. Sans elles, le
 * tremblement du GPS ferait tomber un record une sortie sur deux et les
 * felicitations ne voudraient plus rien dire au bout d'une semaine.
 */
class AchievementDetectorTest {

    private final AchievementDetector detector = new AchievementDetector();

    private static final Instant NOW = Instant.parse("2026-08-15T09:00:00Z");

    @Test
    void celebre_la_premiere_seance_et_rien_d_autre() {
        List<AchievementDetector.Detected> detected =
                detector.detect(SportBests.empty(), candidate(5_000, 1_800, 360, 40));

        // Quatre records d'un coup a quelqu'un qui vient de courir pour la
        // premiere fois sonnerait faux : il n'a rien battu, il a commence.
        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).kind()).isEqualTo(AchievementKind.FIRST_SESSION);
        assertThat(detected.get(0).previousValue()).isNull();
    }

    @Test
    void reconnait_un_record_de_distance_franc() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 40));

        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(6_300, 1_800, 360, 40));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .contains(AchievementKind.LONGEST_DISTANCE);
        AchievementDetector.Detected record = detected.stream()
                .filter(one -> one.kind() == AchievementKind.LONGEST_DISTANCE)
                .findFirst()
                .orElseThrow();
        assertThat(record.previousValue()).isEqualTo(5_000d);
        assertThat(record.improvement()).isEqualTo(1_300d);
        assertThat(record.improvementPercent()).isEqualTo(26d);
    }

    @Test
    void ignore_un_depassement_de_distance_dans_le_bruit_du_gps() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 40));

        // 20 metres de mieux sur 5 km : c'est le tremblement du capteur, pas un
        // record. Le seuil exige 1 % et au moins 50 metres.
        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(5_020, 1_800, 360, 40));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .doesNotContain(AchievementKind.LONGEST_DISTANCE);
    }

    @Test
    void exige_la_marge_absolue_meme_quand_le_pourcentage_est_atteint() {
        // 1 % de 1 000 metres vaut 10 metres : sans plancher absolu, une sortie
        // de 1 011 metres deviendrait un record.
        SportBests bests = bestsOf(row(1_000, 400, 400, 0));

        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(1_030, 400, 400, 0));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .doesNotContain(AchievementKind.LONGEST_DISTANCE);
    }

    @Test
    void n_evalue_pas_l_allure_en_dessous_du_kilometre() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 0));

        // 200 metres a allure fulgurante : l'allure y est du bruit, pas un record.
        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(200, 40, 200, 0));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .doesNotContain(AchievementKind.BEST_AVERAGE_PACE);
    }

    @Test
    void compte_une_allure_plus_basse_comme_un_gain_positif() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 0));

        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(5_000, 1_700, 340, 0));

        AchievementDetector.Detected pace = detected.stream()
                .filter(one -> one.kind() == AchievementKind.BEST_AVERAGE_PACE)
                .findFirst()
                .orElseThrow();
        // 20 secondes gagnees au kilometre : le client doit pouvoir ecrire « +20 »
        // sans se demander le sens du record.
        assertThat(pace.improvement()).isEqualTo(20d);
    }

    @Test
    void ne_retient_pas_une_allure_gagnee_d_une_seule_seconde() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 0));

        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(5_000, 1_795, 359, 0));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .doesNotContain(AchievementKind.BEST_AVERAGE_PACE);
    }

    @Test
    void ecarte_du_record_d_allure_les_seances_trop_courtes_pour_le_detenir() {
        // Un sprint de 300 metres a 3:00/km ne doit pas devenir un record
        // imbattable a vie : il n'entre meme pas dans la reference.
        SportBests bests = bestsOf(
                row(300, 54, 180, 0),
                row(5_000, 1_800, 360, 0));

        assertThat(bests.valueOf(AchievementKind.BEST_AVERAGE_PACE)).isEqualTo(360d);
    }

    @Test
    void ne_declare_pas_de_record_de_parcours_au_premier_passage() {
        assertThat(detector.detectRouteBest(SportType.RUN, null, 2_150, NOW)).isEmpty();
    }

    @Test
    void reconnait_un_meilleur_temps_sur_un_parcours() {
        var detected = detector.detectRouteBest(SportType.RUN, 2_210L, 2_150, NOW);

        assertThat(detected).isPresent();
        assertThat(detected.get().improvement()).isEqualTo(60d);
    }

    @Test
    void ignore_un_gain_de_deux_secondes_sur_un_parcours() {
        assertThat(detector.detectRouteBest(SportType.RUN, 2_210L, 2_208, NOW)).isEmpty();
    }

    @Test
    void une_seance_sans_distance_ne_bat_aucun_record_de_distance() {
        SportBests bests = bestsOf(row(5_000, 1_800, 360, 40));

        // Seance en salle : pas de distance, mais une vraie duree.
        List<AchievementDetector.Detected> detected =
                detector.detect(bests, candidate(0, 3_600, null, 0));

        assertThat(detected).extracting(AchievementDetector.Detected::kind)
                .doesNotContain(AchievementKind.LONGEST_DISTANCE)
                .contains(AchievementKind.LONGEST_MOVING_DURATION);
    }

    private AchievementDetector.Candidate candidate(double distance,
                                                    long movingSeconds,
                                                    Integer pace,
                                                    double elevation) {
        return new AchievementDetector.Candidate(SportType.RUN, distance, movingSeconds, pace, elevation, NOW);
    }

    private SportPerformanceRow row(double distance, long movingSeconds, Integer pace, double elevation) {
        return new SportPerformanceRow(UUID.randomUUID(), NOW.minusSeconds(86_400),
                distance, movingSeconds, pace, elevation);
    }

    private SportBests bestsOf(SportPerformanceRow... rows) {
        return SportBests.from(List.of(rows));
    }
}
