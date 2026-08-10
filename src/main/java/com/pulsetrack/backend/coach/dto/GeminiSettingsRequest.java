package com.pulsetrack.backend.coach.dto;

import com.pulsetrack.backend.coach.CoachingTone;

import jakarta.validation.constraints.NotNull;

/**
 * Preferences de l'assistant. La cle API n'y figure pas : elle a son propre
 * endpoint, pour ne pas transiter a chaque changement de reglage anodin.
 */
public record GeminiSettingsRequest(
        boolean enabled,
        @NotNull CoachingTone coachingTone,
        boolean weeklyReviewEnabled,
        boolean effortWarningsEnabled) {
}
