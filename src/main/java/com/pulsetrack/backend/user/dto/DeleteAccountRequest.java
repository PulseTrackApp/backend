package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Suppression definitive de son propre compte.
 *
 * @param password mot de passe actuel. L'operation efface des annees de seances
 *                 sans retour possible : la demander deux fois n'a pas de sens,
 *                 mais exiger la preuve que l'appareil est bien entre les mains
 *                 de son proprietaire, si
 */
public record DeleteAccountRequest(@NotBlank @Size(max = 100) String password) {
}
