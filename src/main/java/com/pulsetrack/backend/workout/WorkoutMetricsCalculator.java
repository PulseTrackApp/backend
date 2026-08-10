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
            maxSpeedMps = track.maxSpeedMps;
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
                track.maxSpeedMps = Math.max(track.maxSpeedMps, segmentSpeedMps);
            }

            // Vitesse annoncee par le capteur, quand le telephone la fournit.
            if (current.speed() != null && current.speed() > 0) {
                track.maxSpeedMps = Math.max(track.maxSpeedMps, current.speed());
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
        private double maxSpeedMps;
        private double elevationGainMeters;
    }
}
