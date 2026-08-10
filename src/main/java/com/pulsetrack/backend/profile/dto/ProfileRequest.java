package com.pulsetrack.backend.profile.dto;

import java.time.LocalDate;
import java.util.Set;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.profile.FitnessLevel;
import com.pulsetrack.backend.profile.PrimaryGoal;
import com.pulsetrack.backend.profile.Sex;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Enregistrement complet du profil sportif (remplacement, pas fusion).
 *
 * <p>Les bornes ne cherchent pas a etre physiologiquement exactes : elles
 * arretent les saisies aberrantes (170 kg saisi au lieu de 70) qui fausseraient
 * silencieusement toutes les estimations de calories.
 */
public record ProfileRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Min(80) @Max(260) int heightCm,
        @DecimalMin("20.0") @DecimalMax("400.0") double currentWeightKg,
        @Past LocalDate birthDate,
        Sex sex,
        @NotNull PrimaryGoal primaryGoal,
        @NotNull FitnessLevel fitnessLevel,
        @NotEmpty Set<SportType> preferredSports) {
}
