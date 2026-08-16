package com.pulsetrack.backend.achievement.dto;

import java.time.Instant;

import com.pulsetrack.backend.achievement.AchievementKind;
import com.pulsetrack.backend.common.domain.SportType;

/**
 * Un record tombe, pret a etre celebre.
 *
 * <p>Contrat cote client : <strong>une liste non vide vaut felicitations</strong>.
 * Aucun calcul, aucune comparaison, aucun historique a charger.
 *
 * @param label              libelle lisible ; {@code kind} reste la constante de
 *                           protocole et ne doit jamais etre affiche tel quel
 * @param previousValue      valeur depassee ; {@code null} pour un premier
 *                           evenement, qui n'a pas de precedent
 * @param improvement        gain, <strong>toujours positif</strong> — y compris
 *                           pour une allure ou un chronometre, ou la valeur
 *                           baisse. Le client peut donc toujours ecrire « +X »
 *                           sans se demander le sens du record
 * @param improvementPercent gain rapporte au record precedent ; {@code null}
 *                           sans precedent
 * @param headline           titre court de la banniere
 * @param message            constat chiffre, redige cote serveur, pret a afficher
 */
public record AchievementResponse(
        AchievementKind kind,
        String label,
        SportType sportType,
        String unit,
        Double previousValue,
        double newValue,
        Double improvement,
        Double improvementPercent,
        String headline,
        String message,
        Instant achievedAt) {
}
