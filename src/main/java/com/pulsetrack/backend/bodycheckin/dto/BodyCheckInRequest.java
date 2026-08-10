package com.pulsetrack.backend.bodycheckin.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Releve physique declare par l'utilisateur.
 *
 * @param checkinDate        jour du releve ; refuse dans le futur, on ne se pese
 *                           pas a l'avance
 * @param energyLevel        de 1 (epuise) a 5 (en pleine forme)
 * @param averageSleepHours  moyenne de sommeil sur la periode
 */
public record BodyCheckInRequest(
        @NotNull @PastOrPresent LocalDate checkinDate,
        @DecimalMin("20.0") @DecimalMax("400.0") double weightKg,
        @DecimalMin("30.0") @DecimalMax("250.0") Double waistCm,
        @DecimalMin("30.0") @DecimalMax("250.0") Double chestCm,
        @DecimalMin("30.0") @DecimalMax("250.0") Double hipsCm,
        @Min(1) @Max(5) Integer energyLevel,
        @DecimalMin("0.0") @DecimalMax("24.0") Double averageSleepHours,
        @Size(max = 2000) String note) {
}
