package com.pulsetrack.backend.export.dto;

import java.time.Instant;
import java.util.List;

import com.pulsetrack.backend.bodycheckin.dto.BodyCheckInResponse;
import com.pulsetrack.backend.challenge.dto.ChallengeResponse;
import com.pulsetrack.backend.coach.dto.CoachMessageResponse;
import com.pulsetrack.backend.goal.dto.GoalResponse;
import com.pulsetrack.backend.profile.dto.ProfileResponse;
import com.pulsetrack.backend.route.dto.RouteResponse;
import com.pulsetrack.backend.workout.dto.WorkoutResponse;

/**
 * Archive complete des donnees d'un compte.
 *
 * <p>Objectif : que l'utilisateur puisse partir avec tout, et surtout qu'il
 * detienne une copie independante du serveur. C'est la garantie ultime contre la
 * perte de donnees — une sauvegarde qui vit uniquement sur la machine qui heberge
 * la base n'en est pas vraiment une.
 *
 * <p>Les seances sont exportees <strong>avec leur trace GPS complet</strong> :
 * c'est volumineux, mais un parcours ampute n'est pas rejouable et perdrait tout
 * interet.
 *
 * <p>Ce qui n'est volontairement <em>pas</em> exporte : le hash du mot de passe
 * et la cle API Gemini. Les faire sortir du serveur creerait un risque sans
 * rendre service — l'un est irrecuperable par nature, l'autre se regenere en
 * deux clics chez Google.
 *
 * <p>Les seances portent leurs trophees ; les parcours portent leur trace
 * simplifie. Une archive qui ne contiendrait que les chiffres bruts obligerait a
 * tout reconstituer a la main pour retrouver ses circuits.
 *
 * @param exportedAt    instant de generation, pour dater l'archive
 * @param formatVersion version du format ; un import futur saura quoi lire
 */
public record UserDataExport(
        Instant exportedAt,
        int formatVersion,
        String email,
        Instant accountCreatedAt,
        ProfileResponse profile,
        List<WorkoutResponse> workouts,
        List<BodyCheckInResponse> bodyCheckIns,
        List<GoalResponse> goals,
        List<CoachMessageResponse> coachMessages,
        List<RouteResponse> routes,
        List<ChallengeResponse> challenges) {

    /**
     * Incrementer a chaque changement incompatible de la structure exportee.
     *
     * <p>Passe a 2 le 15 aout 2026 : les parcours et les defis s'ajoutent, et les
     * seances portent desormais leurs trophees. L'ajout est compatible en
     * lecture — un outil ecrit pour la version 1 ignore simplement les nouveaux
     * champs — mais une archive de version 1 ne contient pas ces donnees, et un
     * import doit le savoir.
     */
    public static final int CURRENT_FORMAT_VERSION = 2;
}
