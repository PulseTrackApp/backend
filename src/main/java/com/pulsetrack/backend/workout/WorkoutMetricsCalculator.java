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
 * <p><strong>Rien n'est lu directement sur les positions brutes, sauf
 * l'altitude.</strong> Distance, temps en mouvement et pic de vitesse sont lus
 * sur la trajectoire reconstituee par {@link TrackFilter}. Additionner les
 * distances entre points bruts surestime le parcours d'autant plus que les
 * points sont rapproches, et le biais va toujours dans le meme sens : le bruit
 * s'ajoute a chaque segment, il ne s'en retranche jamais.
 *
 * <p>Classe sans etat ni dependance a injecter : elle s'instancie avec
 * {@code new} dans un test unitaire, sans demarrer Spring.
 */
@Component
public class WorkoutMetricsCalculator {

    private final TrackFilter trackFilter = new TrackFilter();

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

        List<TrackFilter.FilteredPoint> estimated = points == null ? List.of() : trackFilter.filter(points);

        if (estimated.size() >= 2) {
            // Le trace fait foi sur la distance annoncee par le client : c'est la
            // seule valeur que le serveur peut verifier.
            distanceMeters = TrackFilter.distanceMeters(estimated);
            // Plafonne au temps total : un trace dont les horodatages depassent
            // la fenetre declaree ne doit pas produire un temps en mouvement
            // superieur a la duree de la seance.
            movingDurationSeconds = Math.min(movingSeconds(estimated), durationSeconds);
            maxSpeedMps = maxSpeedMps(estimated);
            // Le denivele reste lu sur les altitudes brutes : le filtre travaille
            // dans le plan horizontal, ou le bruit fausse la distance, et n'a
            // rien a dire de la verticale.
            elevationGainMeters = elevationGain(points);
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
     * Temps passe en mouvement, lu sur la vitesse estimee.
     *
     * <p>Sur la vitesse estimee et non sur le deplacement mesure entre deux
     * points : a l'arret, le bruit du GPS suffit a faire franchir le seuil a un
     * segment sur deux, et les pauses disparaissaient du decompte.
     */
    private long movingSeconds(List<TrackFilter.FilteredPoint> estimated) {
        long moving = 0;
        for (int i = 1; i < estimated.size(); i++) {
            TrackFilter.FilteredPoint previous = estimated.get(i - 1);
            TrackFilter.FilteredPoint current = estimated.get(i);
            long seconds = Duration.between(previous.at(), current.at()).getSeconds();
            if (seconds <= 0) {
                continue;
            }
            // Les deux extremites doivent etre en mouvement, et non leur
            // moyenne : entre un point a l'arret et le precedent lance a pleine
            // allure, la moyenne reste au-dessus du seuil et l'intervalle
            // entier — parfois plusieurs minutes — passerait pour du mouvement.
            if (Math.min(previous.speedMps(), current.speedMps()) >= MOVING_SPEED_THRESHOLD_MPS) {
                moving += seconds;
            }
        }
        return moving;
    }

    /**
     * Pic de vitesse, lu sur la vitesse estimee.
     *
     * <p>Ce que le filtre rend n'est plus une difference entre deux positions
     * bruitees mais une estimation qui tient compte de la mesure du capteur et
     * de la vraisemblance physique du mouvement. Le pic fantome de 23,5 km/h
     * releve le 11 aout 2026 sur une marche ne peut plus se produire : un point
     * isole trop eloigne est ecarte par le filtre, et la vitesse ne peut pas
     * bondir plus vite que l'acceleration admise.
     */
    private double maxSpeedMps(List<TrackFilter.FilteredPoint> estimated) {
        double max = 0;
        for (TrackFilter.FilteredPoint point : estimated) {
            max = Math.max(max, point.speedMps());
        }
        return max;
    }

    /**
     * Denivele positif cumule, lu sur les altitudes brutes.
     *
     * <p>Le filtre ne travaille que dans le plan horizontal, ou le bruit fausse
     * la distance parcourue. L'altitude a ses propres defauts — un barometre
     * derive avec la meteo, un GPS est trois fois moins precis en vertical qu'en
     * horizontal — mais les traiter demanderait un autre modele, et le seuil
     * d'un metre suffit a ecarter le tremblement du capteur.
     */
    private double elevationGain(List<GpsPointRequest> points) {
        double gain = 0;
        for (int i = 1; i < points.size(); i++) {
            Double previous = points.get(i - 1).altitude();
            Double current = points.get(i).altitude();
            if (previous == null || current == null) {
                continue;
            }
            double climb = current - previous;
            if (climb >= MIN_ELEVATION_DELTA_METERS) {
                gain += climb;
            }
        }
        return gain;
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

}
