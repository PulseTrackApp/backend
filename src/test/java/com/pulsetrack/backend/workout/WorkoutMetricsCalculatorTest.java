package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.List;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.dto.GpsPointRequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du calcul des metriques. Aucune dependance : la classe s'instancie
 * directement, les tests tournent en quelques millisecondes.
 */
class WorkoutMetricsCalculatorTest {

    private static final Instant START = Instant.parse("2026-08-10T06:00:00Z");
    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LON = 2.3522;

    /** Ecart de latitude correspondant a environ 1000 m vers le nord. */
    private static final double ONE_KM_IN_DEGREES_LAT = 0.0089931;

    /** Un metre, en degres, aux abords de Paris. */
    private static final double METER_IN_DEGREES_LAT = ONE_KM_IN_DEGREES_LAT / 1000;

    /** A cette latitude, un degre de longitude couvre environ 73,3 km. */
    private static final double METER_IN_DEGREES_LON = 1 / 73_300d;

    private static final double WEIGHT_KG = 70.0;

    private final WorkoutMetricsCalculator calculator = new WorkoutMetricsCalculator();

    @Test
    void calcule_distance_allure_et_calories_a_partir_du_trace() {
        // 1 km parcouru en 6 minutes, soit 10 km/h et une allure de 6:00/km.
        List<GpsPointRequest> track = List.of(
                point(PARIS_LAT, PARIS_LON, null, START),
                point(PARIS_LAT + ONE_KM_IN_DEGREES_LAT, PARIS_LON, null, START.plusSeconds(360)));

        WorkoutMetrics metrics = calculator.calculate(
                SportType.RUN, START, START.plusSeconds(360), track, null, WEIGHT_KG);

        assertThat(metrics.distanceMeters()).isCloseTo(1000d, org.assertj.core.data.Offset.offset(2d));
        assertThat(metrics.durationSeconds()).isEqualTo(360);
        assertThat(metrics.movingDurationSeconds()).isEqualTo(360);
        assertThat(metrics.averagePaceSecondsPerKm()).isEqualTo(360);
        assertThat(metrics.averageSpeedKmh()).isCloseTo(10d, org.assertj.core.data.Offset.offset(0.05));
        // MET 11,0 (course a ~10 km/h) x 70 kg x 0,1 h = 77 kcal
        assertThat(metrics.caloriesBurned()).isEqualTo(77);
    }

    @Test
    void exclut_les_arrets_du_temps_en_mouvement() {
        // 6 minutes de course, puis 10 minutes quasi immobile (derive GPS).
        List<GpsPointRequest> track = List.of(
                point(PARIS_LAT, PARIS_LON, null, START),
                point(PARIS_LAT + ONE_KM_IN_DEGREES_LAT, PARIS_LON, null, START.plusSeconds(360)),
                point(PARIS_LAT + ONE_KM_IN_DEGREES_LAT + 0.000004, PARIS_LON, null, START.plusSeconds(960)));

        WorkoutMetrics metrics = calculator.calculate(
                SportType.RUN, START, START.plusSeconds(960), track, null, WEIGHT_KG);

        assertThat(metrics.durationSeconds()).isEqualTo(960);
        assertThat(metrics.movingDurationSeconds()).isEqualTo(360);
    }

    @Test
    void ignore_les_micro_variations_d_altitude_mais_compte_les_vraies_montees() {
        List<GpsPointRequest> track = List.of(
                point(PARIS_LAT, PARIS_LON, 100.0, START),
                // +0,4 m : bruit de capteur, ne doit pas compter
                point(PARIS_LAT + 0.0009, PARIS_LON, 100.4, START.plusSeconds(60)),
                // +12 m : vraie montee
                point(PARIS_LAT + 0.0018, PARIS_LON, 112.4, START.plusSeconds(120)));

        WorkoutMetrics metrics = calculator.calculate(
                SportType.RUN, START, START.plusSeconds(120), track, null, WEIGHT_KG);

        assertThat(metrics.elevationGainMeters()).isEqualTo(12.0);
    }

    @Test
    void utilise_la_distance_declaree_quand_il_n_y_a_pas_de_trace() {
        // Seance en salle : 5 km en 1 heure de marche, sans GPS.
        WorkoutMetrics metrics = calculator.calculate(
                SportType.WALK, START, START.plusSeconds(3600), List.of(), 5000d, WEIGHT_KG);

        assertThat(metrics.distanceMeters()).isEqualTo(5000d);
        assertThat(metrics.movingDurationSeconds()).isEqualTo(3600);
        assertThat(metrics.averageSpeedKmh()).isEqualTo(5.0);
        // MET 3,5 (marche a 5 km/h) x 70 kg x 1 h = 245 kcal
        assertThat(metrics.caloriesBurned()).isEqualTo(245);
    }

    @Test
    void ne_renvoie_pas_d_allure_quand_la_distance_est_nulle() {
        WorkoutMetrics metrics = calculator.calculate(
                SportType.OTHER, START, START.plusSeconds(600), List.of(), null, WEIGHT_KG);

        assertThat(metrics.distanceMeters()).isZero();
        assertThat(metrics.averagePaceSecondsPerKm()).isNull();
        assertThat(metrics.averageSpeedKmh()).isZero();
    }

    @Test
    void ne_compte_aucune_calorie_pour_une_seance_de_duree_nulle() {
        WorkoutMetrics metrics = calculator.calculate(
                SportType.RUN, START, START, List.of(), null, WEIGHT_KG);

        assertThat(metrics.durationSeconds()).isZero();
        assertThat(metrics.caloriesBurned()).isZero();
    }

    @Test
    void choisit_le_met_selon_le_sport_et_l_allure() {
        assertThat(calculator.metFor(SportType.WALK, 3.0)).isEqualTo(2.8);
        assertThat(calculator.metFor(SportType.WALK, 5.0)).isEqualTo(3.5);
        assertThat(calculator.metFor(SportType.WALK, 7.0)).isEqualTo(6.3);

        assertThat(calculator.metFor(SportType.RUN, 7.0)).isEqualTo(6.0);
        assertThat(calculator.metFor(SportType.RUN, 10.0)).isEqualTo(11.0);
        assertThat(calculator.metFor(SportType.RUN, 16.0)).isEqualTo(14.5);

        assertThat(calculator.metFor(SportType.RIDE, 12.0)).isEqualTo(4.0);
        assertThat(calculator.metFor(SportType.RIDE, 21.0)).isEqualTo(8.0);
        assertThat(calculator.metFor(SportType.RIDE, 30.0)).isEqualTo(12.0);

        assertThat(calculator.metFor(SportType.OTHER, 42.0)).isEqualTo(5.0);
    }

    @Test
    void la_vitesse_max_ne_descend_jamais_sous_la_moyenne() {
        List<GpsPointRequest> track = List.of(
                point(PARIS_LAT, PARIS_LON, null, START),
                point(PARIS_LAT + ONE_KM_IN_DEGREES_LAT, PARIS_LON, null, START.plusSeconds(360)));

        WorkoutMetrics metrics = calculator.calculate(
                SportType.RUN, START, START.plusSeconds(360), track, null, WEIGHT_KG);

        assertThat(metrics.maxSpeedKmh()).isGreaterThanOrEqualTo(metrics.averageSpeedKmh());
    }

    private GpsPointRequest point(double latitude, double longitude, Double altitude, Instant recordedAt) {
        return new GpsPointRequest(latitude, longitude, altitude, null, null, recordedAt);
    }

    /**
     * Cas reel du 11 aout 2026 : une marche de trente minutes affichait un pic a
     * 23,5 km/h alors que le capteur du telephone n'avait jamais depasse
     * 6,2 km/h. Un seul point a 22,8 metres de precision avait produit un saut
     * lateral d'une vingtaine de metres en trois secondes.
     *
     * <p>Un maximum retient le pire echantillon pour toujours, contrairement a
     * une moyenne ou les ecarts se compensent : c'est la mesure la plus fragile
     * au bruit, et celle qu'on affiche le plus volontiers.
     */
    @Test
    void ignore_un_saut_gps_dans_le_pic_de_vitesse() {
        Instant instant = START;
        double latitude = PARIS_LAT;
        var track = new java.util.ArrayList<GpsPointRequest>();

        // Marche reguliere : 4,2 m toutes les 3 s, soit environ 5 km/h.
        for (int i = 0; i < 200; i++) {
            boolean jitter = i == 100;
            track.add(new GpsPointRequest(
                    latitude,
                    // Le point aberrant est decale de vingt metres sur le cote,
                    // et s'annonce lui-meme tres imprecis.
                    jitter ? PARIS_LON + METER_IN_DEGREES_LON * 20 : PARIS_LON,
                    null,
                    jitter ? 23.0 : 4.0,
                    null,
                    instant));
            latitude += METER_IN_DEGREES_LAT * 4.2;
            instant = instant.plusSeconds(3);
        }

        WorkoutMetrics metrics = calculator.calculate(
                SportType.WALK, START, START.plusSeconds(600), track, null, WEIGHT_KG);

        // Sans le garde-fou, le saut donnait plus de 24 km/h.
        assertThat(metrics.maxSpeedKmh())
                .as("pic de vitesse d'une marche")
                .isLessThan(8d)
                .isGreaterThan(4d);
    }

    /**
     * Le pendant du test precedent : une acceleration franche mais reelle, bien
     * au-dela de l'incertitude annoncee, doit continuer d'etre retenue. Un
     * garde-fou qui ecraserait aussi les vrais pics ne vaudrait pas mieux que le
     * defaut qu'il corrige.
     */
    @Test
    void retient_une_acceleration_reelle() {
        List<GpsPointRequest> track = List.of(
                new GpsPointRequest(PARIS_LAT, PARIS_LON, null, 4.0, null, START),
                // 30 m en 3 s : 36 km/h, largement au-dessus des 4 m d'incertitude.
                new GpsPointRequest(PARIS_LAT + METER_IN_DEGREES_LAT * 30, PARIS_LON, null, 4.0, null,
                        START.plusSeconds(3)));

        WorkoutMetrics metrics = calculator.calculate(
                SportType.RIDE, START, START.plusSeconds(3), track, null, WEIGHT_KG);

        assertThat(metrics.maxSpeedKmh()).isGreaterThan(30d);
    }
}
