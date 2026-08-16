package com.pulsetrack.backend.reminder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.pulsetrack.backend.summary.dto.GoalProgressResponse;

import org.springframework.stereotype.Component;

/**
 * Decide s'il y a lieu d'envoyer un rappel, et avec quel message.
 *
 * <p>Logique separee du declencheur planifie : une decision qui ne s'observe
 * qu'un dimanche a 19 h serait intestable. Ici, elle se verifie en une
 * milliseconde avec des dates choisies.
 */
@Component
public class ReminderDecider {

    /**
     * On ne rappelle la pesee que si la derniere date de plus de 6 jours :
     * quelqu'un qui s'est pese samedi n'a pas besoin d'un rappel le dimanche.
     */
    static final int CHECKIN_REMINDER_AFTER_DAYS = 6;

    /**
     * Seuil d'alerte : en dessous de 60 % de l'objectif hebdomadaire le dimanche
     * matin, la semaine ne sera pas rattrapee sans une sortie supplementaire.
     * Au-dessus, l'ecart se comble sans qu'on ait besoin d'y insister.
     */
    static final double EFFORT_WARNING_THRESHOLD_PERCENT = 60.0;

    /**
     * @param lastCheckinDate date de la derniere pesee, ou vide s'il n'y en a
     *                        jamais eu
     * @param today           jour courant dans le fuseau des rappels
     * @return {@code true} s'il faut rappeler la pesee
     */
    public boolean shouldRemindCheckIn(Optional<LocalDate> lastCheckinDate, LocalDate today) {
        return lastCheckinDate
                .map(last -> ChronoUnit.DAYS.between(last, today) > CHECKIN_REMINDER_AFTER_DAYS)
                // Jamais pese : c'est precisement la personne a qui le rappel sert.
                .orElse(true);
    }

    /**
     * Compose l'alerte d'effort insuffisant.
     *
     * @param goals progression des objectifs de la semaine
     * @return le message a envoyer, ou vide s'il n'y a rien a signaler
     */
    public Optional<String> effortWarningMessage(List<GoalProgressResponse> goals) {
        List<GoalProgressResponse> lagging = goals.stream()
                .filter(goal -> goal.completionPercent() != null)
                .filter(goal -> !goal.achieved())
                .filter(goal -> goal.completionPercent() < EFFORT_WARNING_THRESHOLD_PERCENT)
                .toList();

        if (lagging.isEmpty()) {
            return Optional.empty();
        }

        // On ne cite que l'objectif le plus en retard : une notification qui
        // enumere quatre manques se fait ignorer, une qui en nomme un se lit.
        GoalProgressResponse worst = lagging.stream()
                .min((left, right) -> Double.compare(left.completionPercent(), right.completionPercent()))
                .orElseThrow();

        return Optional.of("Il te reste %s %s à faire pour tenir ton objectif de la semaine."
                .formatted(trim(worst.remaining()), worst.unit()));
    }

    /** Affiche 8 et non 8.0 quand la valeur est entiere. */
    private String trim(Double value) {
        if (value == null) {
            return "0";
        }
        return value == Math.floor(value)
                ? String.valueOf(value.intValue())
                : String.valueOf(value);
    }
}
