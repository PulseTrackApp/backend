package com.pulsetrack.backend.bodycheckin.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param bmi IMC indicatif au moment du releve, calcule avec la taille du profil
 */
public record BodyCheckInResponse(
        UUID id,
        LocalDate checkinDate,
        double weightKg,
        Double waistCm,
        Double chestCm,
        Double hipsCm,
        Integer energyLevel,
        Double averageSleepHours,
        String note,
        Double bmi) {
}
