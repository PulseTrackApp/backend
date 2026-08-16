package com.pulsetrack.backend.route;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le trace simplifie sert a dessiner et a suivre, jamais a mesurer. Ces tests
 * verifient qu'il garde la forme du parcours et qu'il reste borne.
 */
class TrackSimplifierTest {

    private final TrackSimplifier simplifier = new TrackSimplifier();

    /** Un degre de latitude vaut environ 111 320 metres. */
    private static final double METERS_PER_DEGREE = 111_320d;

    @Test
    void garde_les_deux_extremites() {
        List<TrackSimplifier.Point> line = straightLine(200);

        List<TrackSimplifier.Point> kept = simplifier.simplify(line);

        assertThat(kept.get(0)).isEqualTo(line.get(0));
        assertThat(kept.get(kept.size() - 1)).isEqualTo(line.get(line.size() - 1));
    }

    @Test
    void reduit_une_ligne_droite_a_ses_deux_bouts() {
        // Deux cents points parfaitement alignes ne dessinent rien de plus que
        // le segment qui les joint.
        List<TrackSimplifier.Point> kept = simplifier.simplify(straightLine(200));

        assertThat(kept).hasSize(2);
    }

    @Test
    void conserve_un_virage_marque() {
        List<TrackSimplifier.Point> track = new ArrayList<>(straightLine(50));
        // Un ecart de cent metres, bien au-dessus de la tolerance de cinq.
        double lastLatitude = track.get(track.size() - 1).latitude();
        track.add(new TrackSimplifier.Point(lastLatitude, 100 / METERS_PER_DEGREE, null));

        List<TrackSimplifier.Point> kept = simplifier.simplify(track);

        assertThat(kept).hasSizeGreaterThan(2);
    }

    @Test
    void ne_depasse_jamais_le_plafond_de_points() {
        // Une trace en zigzag permanent : chaque point s'ecarte de ses voisins et
        // resisterait a la tolerance nominale.
        List<TrackSimplifier.Point> zigzag = new ArrayList<>();
        for (int index = 0; index < 20_000; index++) {
            double offset = (index % 2 == 0 ? 20 : -20) / METERS_PER_DEGREE;
            zigzag.add(new TrackSimplifier.Point(index * 10d / METERS_PER_DEGREE, offset, null));
        }

        List<TrackSimplifier.Point> kept = simplifier.simplify(zigzag);

        assertThat(kept).hasSizeLessThanOrEqualTo(TrackSimplifier.MAX_POINTS);
    }

    @Test
    void supporte_un_trace_de_deux_points() {
        List<TrackSimplifier.Point> pair = straightLine(2);

        assertThat(simplifier.simplify(pair)).hasSize(2);
    }

    @Test
    void ne_deborde_pas_la_pile_sur_un_tres_long_trace() {
        // Cinquante mille points, le plafond accepte par l'API. Une mise en
        // oeuvre recursive s'ecroulerait ici.
        List<TrackSimplifier.Point> long_ = straightLine(50_000);

        assertThat(simplifier.simplify(long_)).isNotEmpty();
    }

    @Test
    void mesure_correctement_une_distance_connue() {
        TrackSimplifier.Point start = new TrackSimplifier.Point(12.3714, -1.5197, null);
        TrackSimplifier.Point end = new TrackSimplifier.Point(12.3714 + 1_000 / METERS_PER_DEGREE,
                -1.5197, null);

        assertThat(TrackSimplifier.haversineMeters(start, end)).isCloseTo(1_000d, within(5d));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    /** Points alignes sur un meridien, espaces de dix metres. */
    private List<TrackSimplifier.Point> straightLine(int count) {
        List<TrackSimplifier.Point> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new TrackSimplifier.Point(index * 10d / METERS_PER_DEGREE, 0, null));
        }
        return points;
    }
}
