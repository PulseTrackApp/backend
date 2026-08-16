package com.pulsetrack.backend.challenge;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.push.DeviceTokenService;
import com.pulsetrack.backend.push.PushNotification;
import com.pulsetrack.backend.reminder.ReminderProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rappelle les defis dont la date limite approche, et ferme ceux qui l'ont
 * passee.
 *
 * <p>Un balayage quotidien et non un minuteur par defi : une date limite est a la
 * journee, et planifier une tache par defi rendrait le systeme dependant de sa
 * propre memoire — au premier redemarrage, tous les rappels en attente seraient
 * perdus.
 *
 * <p><strong>Limite a connaitre</strong>, la meme que pour les autres rappels :
 * ils ne partent que si le backend tourne a l'heure prevue.
 *
 * <p>Ce composant n'envoie rien pendant un effort. Les alertes a l'approche de
 * l'echeance du chronometre sont jouees par le telephone a partir du plan remis
 * au depart : le serveur ne sait pas ou en est le coureur, et c'est tres bien
 * ainsi.
 */
@Component
@ConditionalOnProperty(prefix = "pulsetrack.reminders", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ChallengeReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChallengeReminderScheduler.class);

    /**
     * On previent a deux jours, un jour, et le jour meme. Plus tot, le rappel
     * n'appelle aucune action ; plus souvent, il devient du bruit et se fait
     * couper.
     */
    static final int REMINDER_HORIZON_DAYS = 2;

    private final ChallengeService challengeService;
    private final DeviceTokenService deviceTokenService;
    private final ZoneId zone;

    public ChallengeReminderScheduler(ChallengeService challengeService,
                                      DeviceTokenService deviceTokenService,
                                      ReminderProperties properties) {
        this.challengeService = challengeService;
        this.deviceTokenService = deviceTokenService;
        this.zone = ZoneId.of(properties.zone());
    }

    /**
     * Ferme les defis jamais tentes dont la date limite est passee, puis previent
     * ceux dont elle approche.
     *
     * <p>La fermeture passe d'abord : sans cela, un defi perime recevrait un
     * rappel « il te reste 0 jour » le matin de sa propre expiration.
     */
    @Scheduled(cron = "${pulsetrack.reminders.challenge-expiry-cron}",
            zone = "${pulsetrack.reminders.zone}")
    public void remindExpiringChallenges() {
        int closed = challengeService.expireOverdue();
        if (closed > 0) {
            log.debug("Defis expires fermes : {}", closed);
        }

        LocalDate today = LocalDate.now(zone);
        for (Challenge challenge : challengeService.draftsExpiringWithin(REMINDER_HORIZON_DAYS)) {
            // Une erreur sur un utilisateur ne doit pas priver les autres de leur
            // rappel : le traitement continue.
            try {
                deviceTokenService.notifyUser(challenge.getUserId(), PushNotification
                        .of("Defi a relever", messageFor(challenge, today))
                        .withRoute("/challenges/" + challenge.getId()));
            } catch (RuntimeException ex) {
                log.warn("Rappel de defi en echec pour l'utilisateur {}", challenge.getUserId(), ex);
            }
        }
    }

    private String messageFor(Challenge challenge, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, challenge.getExpiresOn());
        String cible = "%s en %s".formatted(
                Wording.distance(challenge.getTargetDistanceMeters()),
                Wording.duration(challenge.getTargetDurationSeconds()));

        if (days <= 0) {
            return "Dernier jour pour ton defi : %s.".formatted(cible);
        }
        return days == 1
                ? "Plus qu'un jour pour ton defi : %s.".formatted(cible)
                : "Encore %d jours pour ton defi : %s.".formatted(days, cible);
    }
}
