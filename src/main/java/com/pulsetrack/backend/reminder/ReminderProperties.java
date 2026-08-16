package com.pulsetrack.backend.reminder;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Planification des rappels.
 *
 * <p>Un seul fuseau pour toute l'application : PulseTrack est concu pour un
 * usage personnel, et une planification par utilisateur serait une complexite
 * sans contrepartie. Le jour ou plusieurs utilisateurs vivraient sur des fuseaux
 * differents, c'est ici qu'il faudrait revenir.
 *
 * @param enabled           permet de couper tous les rappels, notamment en test
 * @param zone              fuseau d'interpretation des expressions cron
 * @param weeklyCheckinCron quand rappeler la pesee hebdomadaire
 * @param effortWarningCron quand verifier l'avancement des objectifs
 * @param challengeExpiryCron quand fermer les defis perimes et rappeler ceux
 *                            dont la date limite approche
 */
@ConfigurationProperties(prefix = "pulsetrack.reminders")
@Validated
public record ReminderProperties(
        boolean enabled,
        @NotBlank String zone,
        @NotBlank String weeklyCheckinCron,
        @NotBlank String effortWarningCron,
        @NotBlank String challengeExpiryCron) {
}
