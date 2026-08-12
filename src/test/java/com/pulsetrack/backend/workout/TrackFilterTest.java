package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.pulsetrack.backend.workout.dto.GpsPointRequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eprouve le filtre sur des trajets <strong>simules</strong>, dont on connait la
 * distance exacte.
 *
 * <p>C'est la seule facon de prouver qu'un estimateur est bon. Sur une trace
 * reelle, on ne dispose d'aucune verite a laquelle se comparer : on peut
 * constater que deux methodes divergent, jamais laquelle a raison. Ici on
 * fabrique un deplacement connu, on le bruite comme le ferait un GPS, et on
 * verifie que le filtre retrouve la distance de depart — la ou la somme des
 * positions brutes la surestime franchement.
 *
 * <p>Le bruit est tire d'un generateur a graine fixe : les memes chiffres a
 * chaque execution, sinon un test qui echoue une fois sur vingt ne serait plus
 * un test.
 */
class TrackFilterTest {

    private static final Instant START = Instant.parse("2026-08-12T06:00:00Z");

    /** Ouagadougou, la ou l'application est utilisee. */
    private static final double ORIGIN_LAT = 12.3714;
    private static final double ORIGIN_LON = -1.5197;

    private static final double METERS_PER_DEGREE_LAT = 111_195d;
    private static final double METERS_PER_DEGREE_LON = 111_195d * Math.cos(Math.toRadians(ORIGIN_LAT));

    /** Precision annoncee par un telephone en conditions correctes. */
    private static final double ACCURACY_METERS = 4.0;

    private final TrackFilter filter = new TrackFilter();

    /**
     * Le cas de reference : une marche de vingt minutes a 1,4 m/s, echantillonnee
     * toutes les trois secondes. Distance vraie 1 680 metres.
     */
    @Test
    void retrouve_la_distance_reelle_d_une_marche_bruitee() {
        double trueSpeed = 1.4;
        double seconds = 1200;
        double trueDistance = trueSpeed * seconds;

        List<GpsPointRequest> track = straightWalk(trueSpeed, 3, (int) (seconds / 3), new Random(42));

        double raw = rawDistance(track);
        double filtered = filteredDistance(track);

        // Le defaut qu'on corrige : additionner les positions brutes gonfle le
        // parcours, et toujours dans le meme sens.
        assertThat(raw)
                .as("distance brute, gonflee par le bruit")
                .isGreaterThan(trueDistance * 1.10);

        assertThat(filtered)
                .as("distance filtree")
                .isBetween(trueDistance * 0.95, trueDistance * 1.05);
    }

    /**
     * Le pendant indispensable : un filtre qui raccourcirait tout obtiendrait le
     * bon chiffre sur une marche lente et se tromperait sur une course. On
     * verifie donc a une autre allure et avec un autre pas d'echantillonnage.
     */
    @Test
    void retrouve_la_distance_reelle_d_une_course() {
        double trueSpeed = 3.2;
        double seconds = 900;
        double trueDistance = trueSpeed * seconds;

        List<GpsPointRequest> track = straightWalk(trueSpeed, 5, (int) (seconds / 5), new Random(7));

        assertThat(filteredDistance(track))
                .as("distance filtree d'une course")
                .isBetween(trueDistance * 0.95, trueDistance * 1.05);
    }

    /**
     * Un parcours qui tourne en permanence : c'est la ou un lissage trop
     * energique coupe les virages et perd de la distance. Un tour de piste de
     * quatre cents metres, parcouru trois fois.
     */
    @Test
    void ne_coupe_pas_les_virages() {
        double trueDistance = 1200;
        List<GpsPointRequest> track = laps(400, 3, 3.0, 3, new Random(11));

        assertThat(filteredDistance(track))
                .as("distance filtree sur parcours courbe")
                .isBetween(trueDistance * 0.93, trueDistance * 1.07);
    }

    /**
     * A l'arret, le bruit fait quand meme bouger les positions : sans filtre, une
     * pause de cinq minutes ajoute des dizaines de metres au parcours et fausse
     * la distance comme les calories.
     */
    @Test
    void n_accumule_pas_de_distance_a_l_arret() {
        List<GpsPointRequest> track = standingStill(300, 3, new Random(3));

        assertThat(rawDistance(track))
                .as("distance brute d'une immobilite")
                .isGreaterThan(100d);
        assertThat(filteredDistance(track))
                .as("distance filtree d'une immobilite")
                .isLessThan(30d);
    }

    /**
     * Le point aberrant du 11 aout 2026 : une position isolee tres eloignee, qui
     * s'annonce elle-meme imprecise. Le filtre doit l'ecarter sans que la
     * trajectoire en garde la trace.
     */
    @Test
    void ecarte_une_position_aberrante() {
        List<GpsPointRequest> propre = straightWalk(1.4, 3, 200, new Random(5));
        List<GpsPointRequest> abime = new ArrayList<>(propre);
        GpsPointRequest vise = abime.get(100);
        abime.set(100, new GpsPointRequest(
                vise.latitude() + 60 / METERS_PER_DEGREE_LAT,
                vise.longitude(),
                vise.altitude(),
                22.8,
                vise.speed(),
                vise.recordedAt()));

        double sansAberration = filteredDistance(propre);
        double avecAberration = filteredDistance(abime);

        // Moins de deux metres d'ecart sur 840 : le point a bien ete ignore.
        assertThat(avecAberration).isCloseTo(sansAberration, org.assertj.core.data.Offset.offset(2d));
    }

    /**
     * Sans vitesse capteur, le filtre n'a plus que les positions : il doit rester
     * bien meilleur que leur somme brute, faute de quoi il ne servirait a rien
     * sur les appareils qui n'annoncent rien.
     */
    @Test
    void reste_utile_quand_le_capteur_se_tait() {
        double trueDistance = 1.4 * 1200;
        List<GpsPointRequest> track = straightWalk(1.4, 3, 400, new Random(42)).stream()
                .map(p -> new GpsPointRequest(
                        p.latitude(), p.longitude(), p.altitude(), p.accuracy(), null, p.recordedAt()))
                .toList();

        double raw = rawDistance(track);
        double filtered = filteredDistance(track);

        assertThat(filtered).isLessThan(raw);
        assertThat(filtered)
                .as("distance filtree sans capteur")
                .isBetween(trueDistance * 0.90, trueDistance * 1.10);
    }

    // --- fabrication des traces --------------------------------------------

    /** Marche ou course en ligne droite, bruitee comme le ferait un GPS. */
    private List<GpsPointRequest> straightWalk(double speedMps, int intervalSeconds, int points, Random random) {
        List<GpsPointRequest> track = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double travelled = speedMps * intervalSeconds * i;
            track.add(noisyPoint(0, travelled, speedMps, intervalSeconds * i, random));
        }
        return track;
    }

    /** Tours de piste : un parcours circulaire, donc courbe en permanence. */
    private List<GpsPointRequest> laps(double lapMeters, int laps, double speedMps,
                                       int intervalSeconds, Random random) {
        double radius = lapMeters / (2 * Math.PI);
        int points = (int) (lapMeters * laps / (speedMps * intervalSeconds));

        List<GpsPointRequest> track = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double travelled = speedMps * intervalSeconds * i;
            double angle = travelled / radius;
            track.add(noisyPoint(
                    radius * Math.sin(angle),
                    radius * (1 - Math.cos(angle)),
                    speedMps,
                    intervalSeconds * i,
                    random));
        }
        return track;
    }

    /** Immobilite complete : seul le bruit bouge. */
    private List<GpsPointRequest> standingStill(int seconds, int intervalSeconds, Random random) {
        List<GpsPointRequest> track = new ArrayList<>();
        for (int elapsed = 0; elapsed <= seconds; elapsed += intervalSeconds) {
            track.add(noisyPoint(0, 0, 0, elapsed, random));
        }
        return track;
    }

    /**
     * Un point mesure : la position vraie plus un bruit gaussien de l'ordre de la
     * precision annoncee, et la vitesse capteur bruitee elle aussi, mais bien
     * moins — c'est tout l'interet du Doppler.
     */
    private GpsPointRequest noisyPoint(double eastMeters, double northMeters,
                                       double speedMps, int elapsedSeconds, Random random) {
        double noisyEast = eastMeters + random.nextGaussian() * ACCURACY_METERS;
        double noisyNorth = northMeters + random.nextGaussian() * ACCURACY_METERS;

        return new GpsPointRequest(
                ORIGIN_LAT + noisyNorth / METERS_PER_DEGREE_LAT,
                ORIGIN_LON + noisyEast / METERS_PER_DEGREE_LON,
                null,
                ACCURACY_METERS,
                Math.max(0, speedMps + random.nextGaussian() * 0.15),
                START.plusSeconds(elapsedSeconds));
    }

    // --- mesures -----------------------------------------------------------

    /** Somme des distances entre points bruts : la methode a corriger. */
    private double rawDistance(List<GpsPointRequest> track) {
        double total = 0;
        for (int i = 1; i < track.size(); i++) {
            double deltaNorth = (track.get(i).latitude() - track.get(i - 1).latitude()) * METERS_PER_DEGREE_LAT;
            double deltaEast = (track.get(i).longitude() - track.get(i - 1).longitude()) * METERS_PER_DEGREE_LON;
            total += Math.hypot(deltaEast, deltaNorth);
        }
        return total;
    }

    private double filteredDistance(List<GpsPointRequest> track) {
        return TrackFilter.distanceMeters(filter.filter(track));
    }
}
