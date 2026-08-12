package com.pulsetrack.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmation d'adresse a l'aide du code recu par courriel.
 *
 * @param code code a huit caracteres ; la casse et les espaces sont toleres, le
 *             serveur normalise avant comparaison. L'adresse n'est pas demandee :
 *             le code designe le compte a lui seul
 */
public record VerifyEmailRequest(@NotBlank @Size(max = 32) String code) {
}
