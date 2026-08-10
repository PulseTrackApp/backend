package com.pulsetrack.backend.summary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Compte les jours consecutifs comportant au moins une seance.
 */
@Component
public class ActivityStreakCalculator {

    /**
     * @param activeDays jours distincts avec au moins une seance, dans le fuseau
     *                   de l'utilisateur ; l'ordre n'a pas d'importance
     * @param today      date du jour dans ce meme fuseau
     * @return longueur de la serie en cours, 0 si elle est rompue
     */
    public int streakOf(Collection<LocalDate> activeDays, LocalDate today) {
        if (activeDays.isEmpty()) {
            return 0;
        }

        Set<LocalDate> days = new HashSet<>(activeDays);

        // La journee en cours n'est pas finie : ne pas avoir encore couru
        // aujourd'hui ne doit pas casser une serie de trente jours a 8 h du matin.
        // On repart donc d'hier quand aujourd'hui est vide.
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);

        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
