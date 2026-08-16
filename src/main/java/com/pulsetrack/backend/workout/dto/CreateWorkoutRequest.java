package com.pulsetrack.backend.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.workout.Feeling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Enregistrement d'une seance terminee.
 *
 * <p>Le client n'envoie que ce qu'il a observe : le sport, la fenetre de temps,
 * le trace et son ressenti. Distance, allure, vitesses, denivele et calories sont
 * calcules par le serveur et ne sont pas acceptes en entree.
 *
 * @param id             identifiant choisi par le client, facultatif mais
 *                       recommande : renvoyer deux fois la meme seance avec le
 *                       meme identifiant ne cree pas de doublon. Sans lui, une
 *                       coupure reseau apres l'enregistrement cote serveur —
 *                       frequente sur une heure de course — laisse le mobile
 *                       reessayer et enregistre la seance deux fois.
 * @param distanceMeters distance declaree, prise en compte uniquement quand le
 *                       trace est absent ou trop court (seance en salle)
 * @param gpsPoints      trace du parcours ; peut etre vide ou absent
 * @param routeId        parcours enregistre que cette sortie rejoue, facultatif.
 *                       La reponse porte alors la comparaison avec les passages
 *                       precedents. Le rattachement est <strong>declaratif</strong> :
 *                       le serveur ne verifie pas que la trace suit le circuit,
 *                       comparer deux traces bruitees coute cher et se trompe
 * @param challengeId    defi que cette sortie regle, facultatif. Le defi est
 *                       juge dans le meme appel et son verdict revient dans la
 *                       reponse : un seul aller-retour a l'arrivee, ce qui compte
 *                       quand le reseau revient a peine
 */
public record CreateWorkoutRequest(
        UUID id,
        @NotNull SportType sportType,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @DecimalMin("0.0") Double distanceMeters,
        @Min(1) @Max(10) Integer perceivedEffort,
        Feeling feeling,
        @Size(max = 2000) String note,
        @Size(max = 50_000, message = "trace trop volumineux : 50 000 points maximum")
        List<@Valid GpsPointRequest> gpsPoints,
        UUID routeId,
        UUID challengeId) {
}
