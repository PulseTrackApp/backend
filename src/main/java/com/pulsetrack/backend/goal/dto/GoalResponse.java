package com.pulsetrack.backend.goal.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.goal.GoalType;

/**
 * @param unit unite d'affichage, derivee du type
 */
public record GoalResponse(
        UUID id,
        GoalType type,
        String unit,
        double targetValue,
        LocalDate startDate,
        LocalDate endDate,
        boolean active) {
}
