package com.pulsetrack.backend.workout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.dto.GpsPointRequest;

import org.springframework.stereotype.Component;

/**
 * Calcule les metriques d'une seance a partir de son trace GPS.
 *
 * <p>Le calcul vit cote serveur pour que toutes les plateformes (Android, iOS,
 * web) affichent exactement les memes chiffres, et pour qu'une correction de
 * formule ne demande pas une mise a jour du store.
 *
 * <p>Classe sans etat ni dependance : elle s'instancie avec {@code new} dans un
 * test unitaire, sans demarrer Spring.
 */
@Component
public class WorkoutMetricsCalculator {

    /** Rayon moyen de la Terre, pour la formule de haversine. */
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /**
     * En dessous de 0,5 m/s (1,8 km/h), on considere l'utilisateur a l'arret :
     * c'est l'ordre de grandeur de la derive d'un GPS immobile, qui gonflerait
     * sinon le temps en mouvement pendant les pauses.
     */
    private static final double MOVING_SPEED_THRESHOLD_MPS = 0.5;

    /**
     * Les variations d'altitude inferieures au metre sont du bruit de capteur :
     * les cumuler ferait grimper le denivele de plusieurs dizaines de metres sur
     * un parcours parfaitement plat.
     */
    private static final double MIN_ELEVATION_DELTA_METERS = 1.0;

    /**
     * Precision supposee quand le telephone n'en annonce aucune. Volontairement
     * prudente : sous-estimer le bruit ferait passer pour un pic de vitesse ce
     * qui n'est qu'un point mal localise.
     */
    private static final double DEFAULT_ACCURACY_METERS = 10.0;

    /**
     * Proportion de points munis d'une vitesse capteur a partir de laquelle on
     * cesse de deriver le pic des positions.
     *
     * <p>Quatre cinquiemes : assez pour qu'un trou passager — un tunnel, un
     * demarrage a froid — ne fasse pas basculer tout le calcul, assez peu pour
     * qu'un capteur qui ne parle qu'a l'occasion ne prive pas la seance de son
     * pic.
     */
    private static final double SENSOR_COVERAGE_RATIO = 0.8;

    private static final double SECONDS_PER_HOUR = 3_600d;
    private static final double METERS_PER_KM = 1_000d;

    /**
     * @param sport                   sport pratique, qui determine le MET retenu
     * @param startedAt               debut de la seance
     * @param endedAt                 fin de la seance
     * @param points                  trace GPS <strong>deja trie par ordre
     *                                chronologique</strong> ; peut etre vide
     * @param declaredDistanceMeters  distance annoncee par le client, utilisee
     *                                seulement en l'absence de trace exploitable
     * @param weightKg                poids issu du profil, pour l'estimation calorique
     * @return les metriques a figer sur la seance
     */
    public WorkoutMetrics calculate(SportType sport,
                                    Instant startedAt,
                                    Instant endedAt,
                                    List<GpsPointRequest> points,
                                    Double declaredDistanceMeters,
                                    double weightKg) {

        long durationSeconds = Math.max(0, Duration.between(startedAt, endedAt).getSeconds());

        double distanceMeters;
        long movingDurationSeconds;
        double maxSpeedMps;
        double elevationGainMeters;

        if (points != null && points.size() >= 2) {
            Track track = walkTrack(points);
            // Le trace fait foi sur la distance annoncee par le client : c'est la
            // seule valeur que le serveur peut verifier.
            distanceMeters = track.distanceMeters;
            // Plafonne au temps total : un trace dont les horodatages depassent
            // la fenetre declaree ne doit pas produire un temps en mouvement
            // superieur a la duree de la seance.
            movingDurationSeconds = Math.min(track.movingSeconds, durationSeconds);
            maxSpeedMps = track.maxSpeedMps();
            elevationGainMeters = track.elevationGainMeters;
        } else {
            // Seance sans GPS (tapis, salle, oubli d'autorisation) : on retombe
            // sur ce que l'utilisateur declare, et tout le temps compte.
            distanceMeters = declaredDistanceMeters == null ? 0d : Math.max(0d, declaredDistanceMeters);
            movingDurationSeconds = durationSeconds;
            maxSpeedMps = 0d;
            elevationGainMeters = 0d;
        }

        double movingHours = movingDurationSeconds / SECONDS_PER_HOUR;
        double averageSpeedKmh = movingHours > 0 ? (distanceMeters / METERS_PER_KM) / movingHours : 0d;

        Integer averagePaceSecondsPerKm = null;
        if (distanceMeters > 0 && movingDurationSeconds > 0) {
            averagePaceSecondsPerKm = (int) Math.round(movingDurationSeconds / (distanceMeters / METERS_PER_KM));
        }

        double maxSpeedKmh = Math.max(maxSpeedMps * SECONDS_PER_HOUR / METERS_PER_KM, averageSpeedKmh);
        int calories = estimateCalories(sport, averageSpeedKmh, movingHours, weightKg);

        return new WorkoutMetrics(
                durationSeconds,
                movingDurationSeconds,
                round(distanceMeters, 1),
                averagePaceSecondsPerKm,
                round(averageSpeedKmh, 2),
                round(maxSpeedKmh, 2),
                round(elevationGainMeters, 1),
                calories);
    }

    /**
     * Estime la depense energetique par la methode MET, comme prevu par la spec :
     * {@code calories = MET x poids(kg) x duree(h)}.
     *
     * <p>C'est une estimation de population, pas une mesure : sans capteur
     * cardiaque, l'erreur se compte en dizaines de pourcents. Elle sert a
     * comparer les seances entre elles, pas a piloter un regime.
     */
    int estimateCalories(SportType sport, double averageSpeedKmh, double movingHours, double weightKg) {
        if (movingHours <= 0 || weightKg <= 0) {
            return 0;
        }
        return (int) Math.round(metFor(sport, averageSpeedKmh) * weightKg * movingHours);
    }

    /**
     * Valeur MET selon le sport et l'allure, d'apres le Compendium of Physical
     * Activities (Ainsworth et al.), simplifie en paliers.
     */
    double metFor(SportType sport, double speedKmh) {
        return switch (sport) {
            case WALK -> walkMet(speedKmh);
            case RUN -> runMet(speedKmh);
            case RIDE -> rideMet(speedKmh);
            // Sport non caracterise : intensite moderee par defaut.
            case OTHER -> 5.0;
        };
    }

    private double walkMet(double speedKmh) {
        if (speedKmh < 4.0) {
            return 2.8;   // marche lente
        }
        if (speedKmh < 5.5) {
            return 3.5;   // marche normale
        }
        if (speedKmh < 6.5) {
            return 5.0;   // marche soutenue
        }
        return 6.3;       // marche rapide
    }

    private double runMet(double speedKmh) {
        if (speedKmh < 8.0) {
            return 6.0;   // footing tres lent
        }
        if (speedKmh < 9.7) {
            return 9.8;   // ~8 km/h
        }
        if (speedKmh < 11.3) {
            return 11.0;  // ~10 km/h
        }
        if (speedKmh < 12.9) {
            return 11.8;  // ~12 km/h
        }
        if (speedKmh < 14.5) {
            return 12.8;  // ~14 km/h
        }
        return 14.5;      // au-dela de 14,5 km/h
    }

    private double rideMet(double speedKmh) {
        if (speedKmh < 16.0) {
            return 4.0;   // promenade
        }
        if (speedKmh < 19.3) {
            return 6.8;   // rythme tranquille
        }
        if (speedKmh < 22.5) {
            return 8.0;   // rythme modere
        }
        if (speedKmh < 25.7) {
            return 10.0;  // soutenu
        }
        return 12.0;      // rapide
    }

    /**
     * Parcourt le trace segment par segment et cumule distance, temps en
     * mouvement, vitesse maximale et denivele positif.
     */
    private Track walkTrack(List<GpsPointRequest> points) {
        Track track = new Track();

        for (int i = 1; i < points.size(); i++) {
            GpsPointRequest previous = points.get(i - 1);
            GpsPointRequest current = points.get(i);

            double segmentMeters = haversineMeters(
                    previous.latitude(), previous.longitude(),
                    current.latitude(), current.longitude());
            long segmentSeconds = Duration.between(previous.recordedAt(), current.recordedAt()).getSeconds();

            track.distanceMeters += segmentMeters;

            if (segmentSeconds > 0) {
                double segmentSpeedMps = segmentMeters / segmentSeconds;
                if (segmentSpeedMps >= MOVING_SPEED_THRESHOLD_MPS) {
                    track.movingSeconds += segmentSeconds;
                }
                // Le pic tire des positions ignore les segments dont le
                // deplacement tient dans l'incertitude du GPS. Sans ce
                // garde-fou, un seul point mal localise suffit : une marche du
                // 11 aout 2026 affichait 23,5 km/h — un point a 22,8 metres de
                // precision avait produit un saut de vingt metres en trois
                // secondes.
                //
                // La distance, elle, continue de cumuler ces segments : les
                // ecarts s'y compensent sur la duree, alors qu'un maximum retient
                // le pire d'entre eux pour toujours.
                if (segmentMeters > noiseFloorMeters(previous, current)) {
                    track.positionMaxMps = Math.max(track.positionMaxMps, segmentSpeedMps);
                }
            }

            // Vitesse annoncee par le capteur, quand le telephone la fournit.
            track.segments++;
            if (current.speed() != null) {
                track.sensorSamples++;
                track.sensorMaxMps = Math.max(track.sensorMaxMps, current.speed());
            }

            if (previous.altitude() != null && current.altitude() != null) {
                double climb = current.altitude() - previous.altitude();
                if (climb >= MIN_ELEVATION_DELTA_METERS) {
                    track.elevationGainMeters += climb;
                }
            }
        }
        return track;
    }

    /**
     * Deplacement en deca duquel un segment n'est pas distinguable du bruit du
     * GPS.
     *
     * <p>Retient la moins bonne des deux precisions annoncees : un segment ne
     * vaut pas mieux que son point le plus incertain. Quand le telephone ne
     * renseigne rien, on retombe sur une valeur prudente plutot que sur zero,
     * qui laisserait passer n'importe quel saut.
     */
    private double noiseFloorMeters(GpsPointRequest previous, GpsPointRequest current) {
        double worst = Math.max(
                previous.accuracy() == null ? DEFAULT_ACCURACY_METERS : previous.accuracy(),
                current.accuracy() == null ? DEFAULT_ACCURACY_METERS : current.accuracy());
        return Math.max(worst, 0d);
    }

    /**
     * Distance orthodromique entre deux coordonnees (formule de haversine).
     * Suffisamment precise a l'echelle d'un parcours sportif, et bien plus simple
     * qu'un calcul ellipsoidal.
     */
    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /** Accumulateur interne du parcours du trace. */
    private static final class Track {
        private double distanceMeters;
        private long movingSeconds;
        private double elevationGainMeters;

        /** Nombre de segments parcourus, soit le nombre de points moins un. */
        private int segments;
        /** Segments dont le point d'arrivee portait une vitesse capteur. */
        private int sensorSamples;
        private double sensorMaxMps;
        private double positionMaxMps;

        /**
         * Pic retenu pour la seance.
         *
         * <p><strong>Le capteur prime sur les positions des qu'il couvre le
         * trace.</strong> Sa mesure vient de l'effet Doppler, quand la vitesse
         * tiree des positions n'est qu'une difference entre deux points
         * bruites — et cette difference est d'autant plus fausse que les points
         * sont rapproches. Mesure faite sur une vraie marche du 11 aout 2026,
         * echantillonnee toutes les deux secondes avec quatre metres de
         * precision : le capteur plafonnait a 6,2 km/h la ou les positions
         * annoncaient 11,1 km/h. Marcher deux secondes deplace de 2,8 metres,
         * trois metres de tremblement suffisent donc a doubler la vitesse
         * apparente — un ecart qu'aucun seuil de bruit raisonnable ne rattrape,
         * puisque le deplacement reel est du meme ordre que le bruit.
         *
         * <p>Les positions restent le repli quand le telephone ne dit rien : un
         * pic imparfait vaut mieux qu'un pic absent. Elles reprennent aussi la
         * main si le capteur, bien que present, n'a jamais annonce le moindre
         * mouvement — certains appareils renvoient zero en permanence, et les
         * croire effacerait le pic d'une seance qui a pourtant eu lieu.
         */
        private double maxSpeedMps() {
            boolean sensorCoversTrack = segments > 0
                    && sensorSamples >= SENSOR_COVERAGE_RATIO * segments
                    && sensorMaxMps > 0;
            return sensorCoversTrack ? sensorMaxMps : Math.max(sensorMaxMps, positionMaxMps);
        }
    }
}
