package com.pulsetrack.backend.workout;

import java.util.Comparator;
import java.util.List;

import com.pulsetrack.backend.profile.ProfileService;
import com.pulsetrack.backend.workout.dto.GpsPointRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rejoue le calcul des metriques sur les seances deja enregistrees.
 *
 * <p>Les metriques sont figees en base a l'enregistrement — c'est ce qui permet
 * a l'historique de rester stable et de ne pas recalculer mille points a chaque
 * affichage. La contrepartie est qu'une correction de formule ne repare rien de
 * ce qui existe deja : la marche du 11 aout 2026 a garde son pic fantome de
 * 23,5 km/h longtemps apres que le calcul eut ete corrige.
 *
 * <p><strong>Le recalcul passe par le vrai calculateur, jamais par du SQL.</strong>
 * Deux tentatives precedentes ont reecrit la regle en SQL dans une migration :
 * cela fonctionne pour une colonne, mais des qu'il faut recalculer distance,
 * temps en mouvement, allure, pic et calories a la fois, on reecrit tout un
 * calculateur dans un second langage, sans aucune garantie qu'il dise la meme
 * chose. Ici c'est le code de production qui s'execute, celui-la meme qui est
 * teste.
 *
 * <p>Une limite assumee : les calories sont re-estimees avec le poids
 * <em>actuel</em> du profil. Le poids utilise a l'epoque n'est pas conserve, et
 * le deduire des calories enregistrees serait une reconstitution fragile.
 */
@Service
public class WorkoutMetricsBackfill {

    private static final Logger log = LoggerFactory.getLogger(WorkoutMetricsBackfill.class);

    private final WorkoutSessionRepository sessions;
    private final WorkoutMetricsCalculator calculator;
    private final ProfileService profiles;

    public WorkoutMetricsBackfill(WorkoutSessionRepository sessions,
                                  WorkoutMetricsCalculator calculator,
                                  ProfileService profiles) {
        this.sessions = sessions;
        this.calculator = calculator;
        this.profiles = profiles;
    }

    /**
     * Recalcule toutes les seances et n'ecrit que celles qui changent.
     *
     * <p>Idempotent : relancer l'operation sur des donnees deja corrigees ne
     * touche plus rien, et le compteur le montre.
     *
     * @return le nombre de seances examinees et le nombre reellement corrigees
     */
    @Transactional
    public Result recomputeAll() {
        int examined = 0;
        int updated = 0;

        for (WorkoutSession session : sessions.findAll()) {
            examined++;
            if (recompute(session)) {
                updated++;
            }
        }

        log.info("Recalcul des metriques : {} seances examinees, {} corrigees", examined, updated);
        return new Result(examined, updated);
    }

    private boolean recompute(WorkoutSession session) {
        // L'ordre d'enregistrement fait foi, c'est celui que le trace avait a
        // l'arrivee et celui sur lequel le calcul d'origine a travaille.
        List<GpsPointRequest> track = session.getGpsPoints().stream()
                .sorted(Comparator.comparingInt(GpsPoint::getPosition))
                .map(point -> new GpsPointRequest(
                        point.getLatitude(),
                        point.getLongitude(),
                        point.getAltitude(),
                        point.getAccuracy(),
                        point.getSpeed(),
                        point.getRecordedAt()))
                .toList();

        WorkoutMetrics recomputed = calculator.calculate(
                session.getSportType(),
                session.getStartedAt(),
                session.getEndedAt(),
                track,
                // La distance declaree par le client n'est plus disponible : elle
                // n'a jamais ete conservee. Pour une seance sans trace, la valeur
                // enregistree est precisement cette distance declaree, on la
                // repasse donc telle quelle plutot que de la ramener a zero.
                session.getDistanceMeters(),
                profiles.weightKgOf(session.getUserId()));

        if (isSameAs(session, recomputed)) {
            return false;
        }

        log.info("Seance {} : distance {} -> {} m, pic {} -> {} km/h",
                session.getId(), session.getDistanceMeters(), recomputed.distanceMeters(),
                session.getMaxSpeedKmh(), recomputed.maxSpeedKmh());
        session.applyMetrics(recomputed);
        return true;
    }

    /**
     * Comparaison sur les valeurs deja arrondies telles qu'elles sont stockees :
     * comparer des flottants bruts ferait passer pour un changement un ecart
     * invisible a l'affichage, et le compteur de corrections perdrait son sens.
     */
    private boolean isSameAs(WorkoutSession session, WorkoutMetrics metrics) {
        return session.getDurationSeconds() == metrics.durationSeconds()
                && session.getMovingDurationSeconds() == metrics.movingDurationSeconds()
                && session.getDistanceMeters() == metrics.distanceMeters()
                && session.getAverageSpeedKmh() == metrics.averageSpeedKmh()
                && session.getMaxSpeedKmh() == metrics.maxSpeedKmh()
                && session.getElevationGainMeters() == metrics.elevationGainMeters()
                && session.getCaloriesBurned() == metrics.caloriesBurned();
    }

    /**
     * @param sessionsExamined seances parcourues
     * @param sessionsUpdated  seances dont au moins une metrique a change
     */
    public record Result(int sessionsExamined, int sessionsUpdated) {
    }
}
