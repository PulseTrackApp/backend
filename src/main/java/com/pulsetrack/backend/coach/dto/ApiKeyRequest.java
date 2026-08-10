package com.pulsetrack.backend.coach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Depot de la cle API Gemini de l'utilisateur.
 *
 * <p>Aucune contrainte de format au-dela de la longueur : Google peut changer la
 * forme de ses cles, et une expression reguliere trop stricte rejetterait demain
 * une cle parfaitement valide. La verification utile est un appel reel.
 */
public record ApiKeyRequest(@NotBlank @Size(min = 20, max = 200) String apiKey) {
}
