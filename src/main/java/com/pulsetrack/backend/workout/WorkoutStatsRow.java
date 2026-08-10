package com.pulsetrack.backend.workout;

import java.time.Instant;

import com.pulsetrack.backend.common.domain.SportType;

/**
 * Projection legere d'une seance pour le calcul des statistiques.
 *
 * <p>On ne charge ni les notes, ni le ressenti, ni surtout le trace GPS : une
 * annee d'historique represente quelques centaines de lignes de ce type, contre
 * potentiellement des millions de points GPS.
 *
 * <p>L'agregation est ensuite faite en Java plutot qu'en SQL. Le decoupage en
 * jours et en mois depend du fuseau de l'utilisateur, ce qui en SQL impose des
 * requetes natives difficiles a tester ; en Java, c'est une fonction pure.
 * A l'echelle d'un suivi personnel — quelques centaines de seances par an — le
 * cout est negligeable. Au-dela de plusieurs dizaines de milliers de seances, il
 * faudrait basculer l'agregation en base.
 */
public record WorkoutStatsRow(
        Instant startedAt,
        SportType sportType,
        double distanceMeters,
        long movingDurationSeconds,
        int caloriesBurned,
        double elevationGainMeters,
        Integer averagePaceSecondsPerKm) {
}
