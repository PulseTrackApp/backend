package com.pulsetrack.backend.stats.dto;

import java.time.LocalDate;

/**
 * Un point de la serie temporelle, destine aux graphiques.
 *
 * <p>Les intervalles vides sont presents avec des totaux a zero : une courbe
 * dont les jours sans sport manquent donnerait l'illusion d'une activite
 * continue.
 *
 * @param start debut de l'intervalle (le jour, ou le 1er du mois)
 * @param label libelle pret a afficher
 */
public record StatsBucket(LocalDate start, String label, StatsTotals totals) {
}
