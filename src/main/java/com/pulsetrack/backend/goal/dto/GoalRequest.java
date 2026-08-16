package com.pulsetrack.backend.goal.dto;

import java.time.LocalDate;

import com.pulsetrack.backend.goal.GoalType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Objectif a fixer ou a modifier.
 *
 * @param targetValue valeur cible, dans l'unite du type (km, min, seances, kcal, kg)
 * @param startDate   debut de prise en compte ; par defaut aujourd'hui
 * @param endDate     echeance facultative
 */
public record GoalRequest(
        @NotNull GoalType type,
        @DecimalMin(value = "0.0", inclusive = false, message = "la cible doit être strictement positive")
        @DecimalMax("100000.0")
        double targetValue,
        LocalDate startDate,
        LocalDate endDate) {
}
