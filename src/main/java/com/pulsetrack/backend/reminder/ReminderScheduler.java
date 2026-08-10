package com.pulsetrack.backend.reminder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.coach.GeminiSettings;
import com.pulsetrack.backend.coach.GeminiSettingsService;
import com.pulsetrack.backend.push.DeviceTokenService;
import com.pulsetrack.backend.push.PushNotification;
import com.pulsetrack.backend.summary.WeeklySummaryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Declenche les rappels aux heures prevues.
 *
 * <p><strong>Limite a connaitre :</strong> ces rappels ne partent que si le
 * backend tourne au moment prevu. Lance sur un poste de travail eteint le soir,
 * le rappel du dimanche 19 h n'existera pas. Il faut un serveur allume en
 * permanence pour que la promesse soit tenue.
 *
 * <p>Cette classe ne contient aucune regle : elle orchestre. Les decisions sont
 * dans {@link ReminderDecider}, qui se teste sans attendre dimanche.
 */
@Component
@ConditionalOnProperty(prefix = "pulsetrack.reminders", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final DeviceTokenService deviceTokenService;
    private final BodyCheckInService bodyCheckInService;
    private final WeeklySummaryService weeklySummaryService;
    private final GeminiSettingsService geminiSettingsService;
    private final ReminderDecider decider;
    private final ZoneId zone;

    public ReminderScheduler(DeviceTokenService deviceTokenService,
                             BodyCheckInService bodyCheckInService,
                             WeeklySummaryService weeklySummaryService,
                             GeminiSettingsService geminiSettingsService,
                             ReminderDecider decider,
                             ReminderProperties properties) {
        this.deviceTokenService = deviceTokenService;
        this.bodyCheckInService = bodyCheckInService;
        this.weeklySummaryService = weeklySummaryService;
        this.geminiSettingsService = geminiSettingsService;
        this.decider = decider;
        this.zone = ZoneId.of(properties.zone());
    }

    /**
     * Rappel de la pesee hebdomadaire, uniquement pour ceux qui ne l'ont pas
     * faite recemment.
     */
    @Scheduled(cron = "${pulsetrack.reminders.weekly-checkin-cron}",
            zone = "${pulsetrack.reminders.zone}")
    public void remindWeeklyCheckIn() {
        LocalDate today = LocalDate.now(zone);
        List<UUID> recipients = deviceTokenService.userIdsWithDevices();
        log.debug("Rappel de pesee : {} destinataire(s) potentiel(s)", recipients.size());

        for (UUID userId : recipients) {
            // Une erreur sur un utilisateur ne doit pas priver les autres de leur
            // rappel : le traitement continue.
            try {
                if (!decider.shouldRemindCheckIn(bodyCheckInService.lastCheckInDateOf(userId), today)) {
                    continue;
                }
                deviceTokenService.notifyUser(userId, PushNotification
                        .of("Pesee hebdomadaire", "Deux minutes pour noter ton poids et ton ressenti.")
                        .withRoute("/body-checkin"));
            } catch (RuntimeException ex) {
                log.warn("Rappel de pesee en echec pour l'utilisateur {}", userId, ex);
            }
        }
    }

    /**
     * Alerte quand les objectifs de la semaine sont loin d'etre atteints.
     *
     * <p>Le calcul est fait par nos soins, sans appeler Gemini : une alerte ne
     * doit pas dependre d'une cle tierce ni consommer de quota.
     */
    @Scheduled(cron = "${pulsetrack.reminders.effort-warning-cron}",
            zone = "${pulsetrack.reminders.zone}")
    public void warnAboutInsufficientEffort() {
        for (UUID userId : deviceTokenService.userIdsWithDevices()) {
            try {
                if (!effortWarningsEnabled(userId)) {
                    continue;
                }
                var summary = weeklySummaryService.summarize(userId, null, zone);
                decider.effortWarningMessage(summary.goals()).ifPresent(message ->
                        deviceTokenService.notifyUser(userId, PushNotification
                                .of("Objectif de la semaine", message)
                                .withRoute("/dashboard")));
            } catch (RuntimeException ex) {
                log.warn("Alerte d'effort en echec pour l'utilisateur {}", userId, ex);
            }
        }
    }

    /**
     * Le reglage vit dans les preferences de l'assistant, comme le prevoit la
     * spec produit. Absence de reglages = valeur par defaut, c'est-a-dire actif.
     */
    private boolean effortWarningsEnabled(UUID userId) {
        return geminiSettingsService.findExisting(userId)
                .map(GeminiSettings::isEffortWarningsEnabled)
                .orElse(true);
    }
}
