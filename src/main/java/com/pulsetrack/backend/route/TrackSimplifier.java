package com.pulsetrack.backend.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Reduit un trace GPS a la poignee de points qui en dessinent la forme.
 *
 * <p>Une heure de course a un point par seconde fait 3 600 points. Les renvoyer a
 * chaque affichage d'un parcours coute un demi-megaoctet par carte, pour un
 * dessin que l'ecran ne peut pas distinguer d'un trace a trois cents points.
 *
 * <p><strong>Ce que ce trace n'est pas.</strong> Il sert a dessiner et a suivre,
 * jamais a mesurer. La distance d'un parcours reste celle de la seance d'origine,
 * estimee par le filtre de Kalman ; la recalculer sur ces points, bruts et
 * decimes, ramenerait exactement la surestimation que le filtre corrige.
 *
 * <p>Algorithme de Ramer-Douglas-Peucker : on garde les extremites, on cherche le
 * point le plus eloigne du segment qui les joint, et on recommence de part et
 * d'autre tant que cet ecart depasse la tolerance. Ce qui reste est le trace
 * epure de ses points alignes.
 *
 * <p>Implementation iterative et non recursive : un trace en epingle a cheveux
 * peut engendrer une recursion aussi profonde qu'il y a de points, et 50 000
 * points — le plafond accepte par l'API — feraient deborder la pile.
 *
 * <p>Classe sans etat : elle s'instancie avec {@code new} dans un test unitaire.
 */
@Component
public class TrackSimplifier {

    /**
     * Tolerance, en metres. Un point qui s'ecarte de moins de cela de la ligne
     * de ses voisins n'apporte rien : c'est l'ordre de grandeur du bruit d'un
     * GPS de telephone, et bien en deca de ce qu'un ecran affiche.
     */
    public static final double TOLERANCE_METERS = 5.0;

    /**
     * Plafond de points conserves. Au-dela, la tolerance est relevee jusqu'a
     * repasser dessous : un ultra-trail de dix heures ne doit pas rendre une
     * reponse de plusieurs megaoctets.
     */
    public static final int MAX_POINTS = 2_000;

    /** Metres par degre de latitude. Constant a la precision qui nous occupe. */
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320d;

    /**
     * Un point du trace, reduit a sa geometrie.
     */
    public record Point(double latitude, double longitude, Double altitude) {
    }

    /**
     * @param points trace complet, dans l'ordre chronologique
     * @return les points conserves, extremites comprises, dans le meme ordre
     */
    public List<Point> simplify(List<Point> points) {
        if (points.size() <= 2) {
            return List.copyOf(points);
        }

        double tolerance = TOLERANCE_METERS;
        List<Point> kept = simplify(points, tolerance);
        // Le nombre de points conserves decroit quand la tolerance monte, mais
        // pas proportionnellement : on double jusqu'a repasser sous le plafond
        // plutot que de tenter une formule qui se tromperait sur un trace tordu.
        while (kept.size() > MAX_POINTS) {
            tolerance *= 2;
            kept = simplify(points, tolerance);
        }
        return kept;
    }

    private List<Point> simplify(List<Point> points, double toleranceMeters) {
        BitSet keep = new BitSet(points.size());
        keep.set(0);
        keep.set(points.size() - 1);

        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {0, points.size() - 1});

        while (!pending.isEmpty()) {
            int[] span = pending.pop();
            int from = span[0];
            int to = span[1];
            if (to - from < 2) {
                continue;
            }

            int farthest = -1;
            double farthestDistance = 0;
            for (int index = from + 1; index < to; index++) {
                double distance = distanceToSegment(points.get(index), points.get(from), points.get(to));
                if (distance > farthestDistance) {
                    farthestDistance = distance;
                    farthest = index;
                }
            }

            if (farthestDistance > toleranceMeters) {
                keep.set(farthest);
                pending.push(new int[] {from, farthest});
                pending.push(new int[] {farthest, to});
            }
        }

        List<Point> kept = new ArrayList<>(keep.cardinality());
        for (int index = keep.nextSetBit(0); index >= 0; index = keep.nextSetBit(index + 1)) {
            kept.add(points.get(index));
        }
        return kept;
    }

    /**
     * Distance d'un point au segment, en metres.
     *
     * <p>Projection equirectangulaire : les degres de longitude sont resserres
     * par le cosinus de la latitude, ceux de latitude sont constants. Sur les
     * quelques centaines de metres que couvre un segment, l'approximation vaut
     * la formule spherique exacte et coute une fraction de son temps de calcul —
     * ce qui compte, l'algorithme la sollicitant des dizaines de milliers de fois.
     */
    private double distanceToSegment(Point point, Point start, Point end) {
        double latitudeScale = Math.cos(Math.toRadians(start.latitude()));

        double px = (point.longitude() - start.longitude()) * latitudeScale * METERS_PER_DEGREE_LATITUDE;
        double py = (point.latitude() - start.latitude()) * METERS_PER_DEGREE_LATITUDE;
        double sx = (end.longitude() - start.longitude()) * latitudeScale * METERS_PER_DEGREE_LATITUDE;
        double sy = (end.latitude() - start.latitude()) * METERS_PER_DEGREE_LATITUDE;

        double segmentLengthSquared = sx * sx + sy * sy;
        // Segment de longueur nulle — deux points identiques, ce qui arrive quand
        // le telephone repete une position a l'arret. La distance au segment se
        // ramene a la distance au point.
        if (segmentLengthSquared == 0) {
            return Math.hypot(px, py);
        }

        // Projection bornee a [0, 1] : au-dela des extremites, c'est la distance
        // a l'extremite qui compte, pas a la droite prolongee.
        double projection = Math.max(0, Math.min(1, (px * sx + py * sy) / segmentLengthSquared));
        return Math.hypot(px - projection * sx, py - projection * sy);
    }

    /**
     * Distance entre deux points, en metres, par la formule de haversine.
     *
     * <p>Elle ne sert qu'a repartir la distance officielle du parcours le long du
     * trace : les proportions entre segments sont justes meme si leur somme, sur
     * des points bruts, surestime le parcours reel.
     */
    public static double haversineMeters(Point from, Point to) {
        double earthRadius = 6_371_000d;
        double deltaLatitude = Math.toRadians(to.latitude() - from.latitude());
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
