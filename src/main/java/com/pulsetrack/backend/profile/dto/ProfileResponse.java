package com.pulsetrack.backend.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BmiCategory;
import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.profile.FitnessLevel;
import com.pulsetrack.backend.profile.PrimaryGoal;
import com.pulsetrack.backend.profile.Sex;

/**
 * @param age  age en annees revolues, calcule cote serveur pour que tous les
 *             clients affichent la meme valeur
 * @param bmi  IMC indicatif, arrondi a une decimale
 * @param bmiCategory categorie indicative de l'IMC, fournie par le serveur pour
 *                    eviter de dupliquer les seuils dans les clients
 */
public record ProfileResponse(
        UUID id,
        String displayName,
        int heightCm,
        double currentWeightKg,
        LocalDate birthDate,
        Integer age,
        Sex sex,
        PrimaryGoal primaryGoal,
        FitnessLevel fitnessLevel,
        Set<SportType> preferredSports,
        Double bmi,
        BmiCategory bmiCategory,
        Instant createdAt,
        Instant updatedAt) {
}
