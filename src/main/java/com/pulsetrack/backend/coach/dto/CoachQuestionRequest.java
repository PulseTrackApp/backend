package com.pulsetrack.backend.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question question libre ; bornee en longueur car elle est transmise a
 *                 un service facture au volume, et une question de dix pages
 *                 n'appelle pas une meilleure reponse
 */
public record CoachQuestionRequest(@NotBlank @Size(max = 500) String question) {
}
