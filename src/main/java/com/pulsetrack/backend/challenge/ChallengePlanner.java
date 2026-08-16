package com.pulsetrack.backend.challenge;

import java.util.ArrayList;
import java.util.List;

import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.Cue;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.CueKind;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.CueTrigger;
import com.pulsetrack.backend.challenge.dto.ChallengePlanResponse.Split;
import com.pulsetrack.backend.motivation.Wording;

import org.springframework.stereotype.Component;

/**
 * Compose le tableau de marche remis au depart d'un defi.
 *
 * <p><strong>Pourquoi un plan plutot que des appels en cours de course.</strong>
 * Les alertes a l'approche de l'echeance sont ce qui compte le plus dans un defi,
 * et ce sont elles qui tomberaient en premier : le reseau est mauvais quand on
 * bouge, et une alerte qui attend une reponse HTTP arrive apres l'echeance
 * qu'elle annonce. Le serveur remet donc tout d'avance — seuils et messages — et
 * le telephone les joue seul, meme en mode avion.
 *
 * <p>Classe sans etat ni dependance : elle s'eprouve avec un simple {@code new}.
 */
@Component
public class ChallengePlanner {

    /**
     * Nombre maximal de jalons. Au-dela, la liste devient un mur de chiffres que
     * personne ne lit — un cent kilometres n'a pas besoin de cent lignes, et un
     * dix kilometres se jalonne au kilometre, pas tous les cinq cents metres.
     */
    static final int MAX_SPLITS = 12;

    /** Paliers de jalonnage, du plus fin au plus large, en metres. */
    private static final double[] SPLIT_STEPS = {500, 1_000, 2_000, 5_000, 10_000};

    /**
     * En dessous de ce total, l'alerte « cinq minutes » n'a pas de sens : elle
     * tomberait avant meme la moitie de l'effort.
     */
    private static final long FIVE_MINUTE_ALERT_MIN_DURATION = 600;

    /** Sous cette distance, l'alerte des cinq cents derniers metres arrive trop tot. */
    private static final double FINAL_PUSH_MIN_DISTANCE = 1_500;

    public ChallengePlanResponse planFor(Challenge challenge) {
        return new ChallengePlanResponse(
                challenge.requiredPaceSecondsPerKm(),
                round(challenge.requiredSpeedKmh()),
                splitsOf(challenge),
                cuesOf(challenge));
    }

    /**
     * Jalons regulierement espaces, avec l'heure de passage a tenir.
     *
     * <p>Le pas s'elargit avec la distance de facon que le nombre de jalons reste
     * lisible. La cible elle-meme n'est pas un jalon : c'est l'arrivee, elle a
     * son propre traitement a l'ecran.
     */
    private List<Split> splitsOf(Challenge challenge) {
        double target = challenge.getTargetDistanceMeters();
        double step = stepFor(target);

        List<Split> splits = new ArrayList<>();
        int index = 1;
        for (double distance = step; distance < target; distance += step, index++) {
            long targetElapsed = Math.round(challenge.getTargetDurationSeconds() * (distance / target));
            splits.add(new Split(index, round(distance), targetElapsed, labelOf(distance)));
        }
        return List.copyOf(splits);
    }

    private double stepFor(double targetMeters) {
        for (double step : SPLIT_STEPS) {
            if (targetMeters / step <= MAX_SPLITS) {
                return step;
            }
        }
        // Distance enorme : on tranche en vingt-cinq parts egales plutot que de
        // rendre une liste interminable au dernier palier.
        return targetMeters / MAX_SPLITS;
    }

    private String labelOf(double distanceMeters) {
        // « km 3 » se lit d'un coup d'oeil sur un ecran secoue ; « 3,00 km » non.
        if (distanceMeters % 1_000 == 0) {
            return "km " + (int) (distanceMeters / 1_000);
        }
        return Wording.distance(distanceMeters);
    }

    /**
     * Les messages a jouer pendant l'effort.
     *
     * <p>Peu nombreux et espaces : une application qui parle toutes les deux
     * minutes se fait couper le son, et l'alerte d'echeance se perd avec le reste.
     */
    private List<Cue> cuesOf(Challenge challenge) {
        List<Cue> cues = new ArrayList<>();
        double distance = challenge.getTargetDistanceMeters();
        long duration = challenge.getTargetDurationSeconds();
        String pace = Wording.pace(challenge.requiredPaceSecondsPerKm());

        cues.add(new Cue(CueTrigger.ELAPSED_PERCENT, 25, CueKind.MOTIVATION,
                "Bien lance",
                "Un quart du temps est passe. A " + pace + ", ce rythme te mene au bout."));

        cues.add(new Cue(CueTrigger.DISTANCE_PERCENT, 50, CueKind.MOTIVATION,
                "Moitie faite",
                Wording.distance(distance / 2) + " derriere toi. La seconde moitie se joue maintenant."));

        cues.add(new Cue(CueTrigger.ELAPSED_PERCENT, 75, CueKind.PACE_WARNING,
                "Dernier quart",
                "Trois quarts du temps ecoules. Regarde ce qu'il te reste et ajuste."));

        // Sur un defi court, l'alerte des cinq minutes tomberait avant la moitie
        // de l'effort : elle inquieterait pour rien.
        if (duration > FIVE_MINUTE_ALERT_MIN_DURATION) {
            cues.add(new Cue(CueTrigger.REMAINING_SECONDS, 300, CueKind.DEADLINE_ALERT,
                    "5 minutes",
                    "Cinq minutes avant l'echeance. C'est le moment de donner ce qu'il reste."));
        }

        cues.add(new Cue(CueTrigger.REMAINING_SECONDS, 60, CueKind.DEADLINE_ALERT,
                "Derniere minute",
                "Soixante secondes. Tout se joue la."));

        if (distance > FINAL_PUSH_MIN_DISTANCE) {
            cues.add(new Cue(CueTrigger.DISTANCE_REMAINING_METERS, 500, CueKind.FINAL_PUSH,
                    "Dernier effort",
                    "500 metres. Tu y es."));
        }

        return List.copyOf(cues);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
