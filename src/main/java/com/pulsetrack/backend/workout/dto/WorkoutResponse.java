package com.pulsetrack.backend.workout.dto;

import java.util.List;

/**
 * Vue detaillee d'une seance : le resume, plus le trace complet.
 *
 * <p>Compose le resume au lieu d'en recopier les quinze champs : un champ ajoute
 * a l'historique apparait automatiquement ici.
 */
public record WorkoutResponse(WorkoutSummaryResponse summary, List<GpsPointResponse> gpsPoints) {
}
