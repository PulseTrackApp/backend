package com.pulsetrack.backend.challenge.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un defi a se poser : couvrir cette distance dans ce temps.
 *
 * <p>Les bornes ne sont pas decoratives. Sous cent metres ou sous une minute, il
 * n'y a pas d'effort a chronometrer ; au-dela de cinq cents kilometres ou de
 * vingt-quatre heures, c'est une faute de frappe, et l'accepter produirait un
 * plan de course absurde et des alertes qui ne partiraient jamais.
 *
 * @param title     nom du defi ; engendre a partir de la cible s'il est absent
 * @param routeId   circuit sur lequel le defi se joue, facultatif
 * @param expiresOn date limite pour <strong>tenter</strong> le defi, facultative.
 *                  A ne pas confondre avec l'echeance du chronometre, qui se
 *                  deduit de la duree cible au moment du depart
 */
public record CreateChallengeRequest(
        @Size(max = 120) String title,
        @NotNull SportType sportType,
        @DecimalMin(value = "100.0", message = "un defi porte sur 100 metres au minimum")
        @DecimalMax(value = "500000.0", message = "500 kilometres au maximum")
        double targetDistanceMeters,
        @Min(value = 60, message = "un defi dure au moins une minute")
        @Max(value = 86_400, message = "24 heures au maximum")
        long targetDurationSeconds,
        UUID routeId,
        LocalDate expiresOn) {
}
