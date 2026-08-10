package com.pulsetrack.backend.coach;

import java.util.List;

import com.pulsetrack.backend.bodycheckin.dto.BodyProgressResponse;
import com.pulsetrack.backend.profile.dto.ProfileResponse;
import com.pulsetrack.backend.summary.dto.WeeklySummaryResponse;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

/**
 * Tout ce que le coach doit savoir pour repondre : profil, semaine ecoulee,
 * evolution physique et seances recentes.
 *
 * <p>Type intermediaire assume : il rend {@link CoachPromptBuilder} testable
 * sans base de donnees, et rend visible d'un coup d'oeil ce que l'on transmet a
 * un service tiers — ce qui compte, s'agissant de donnees de sante.
 *
 * @param recentSessions seances les plus recentes, sans leur trace GPS
 */
public record CoachContext(
        ProfileResponse profile,
        WeeklySummaryResponse week,
        BodyProgressResponse body,
        List<WorkoutSummaryResponse> recentSessions) {
}
