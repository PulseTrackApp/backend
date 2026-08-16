package com.pulsetrack.backend.coach;

import java.util.StringJoiner;

import com.pulsetrack.backend.summary.dto.GoalProgressResponse;
import com.pulsetrack.backend.workout.dto.WorkoutSummaryResponse;

import org.springframework.stereotype.Component;

/**
 * Compose les textes envoyes a Gemini : le cadre (instruction systeme) et les
 * donnees (message utilisateur).
 *
 * <p>Les garde-fous de la spec produit — pas de diagnostic medical, pas d'effort
 * dangereux, invitation a consulter un professionnel — sont ecrits ici, en un
 * seul endroit. Les disperser dans chaque appel garantirait d'en oublier un.
 *
 * <p>Classe sans etat ni dependance : le prompt se verifie dans un test unitaire.
 */
@Component
public class CoachPromptBuilder {

    /** Nombre de seances detaillees transmises au modele. */
    static final int RECENT_SESSIONS_IN_PROMPT = 8;

    /**
     * Cadre de reponse commun a tous les appels.
     *
     * @param tone registre de langage choisi par l'utilisateur
     */
    public String systemInstruction(CoachingTone tone) {
        return """
                Tu es le coach sportif personnel de l'utilisateur de PulseTrack, une application \
                de suivi de course, velo et marche. Tu t'exprimes en francais, sur un ton %s.

                REGLES ABSOLUES, sans exception :
                - Tu n'es pas medecin. Tu ne poses aucun diagnostic, tu ne nommes aucune pathologie \
                et tu ne recommandes aucun traitement, complement ou medicament.
                - Si les donnees ou la question evoquent une douleur, un malaise, un essoufflement \
                anormal, une blessure ou une pathologie, ta premiere phrase invite a consulter un \
                professionnel de sante, et tu ne proposes aucun entrainement tant que ce n'est pas fait.
                - Tu ne proposes jamais un effort disproportionne par rapport au niveau declare. \
                Toute progression de volume reste progressive.
                - Tu ne prescris aucun regime alimentaire ni restriction calorique.
                - Tes conseils sont des propositions. L'utilisateur reste libre de les ignorer, et \
                tu ne culpabilises jamais une semaine sans activite.

                FORME DE TA REPONSE :
                - 150 mots maximum, en francais courant, sans jargon.
                - Appuie-toi sur les chiffres fournis et cite-les.
                - Termine par une seule action concrete et realisable dans les jours qui viennent, \
                avec une duree ou une distance precise.
                - Pas de titre, pas de liste a puces, pas de formule d'accueil. Va droit au fait.
                """.formatted(tone.promptDescription());
    }

    /** Message demandant le bilan de la semaine ecoulee. */
    public String weeklyReviewPrompt(CoachContext context) {
        return """
                Voici mes donnees. Fais le bilan de ma semaine et dis-moi si mon effort est \
                suffisant, insuffisant ou excessif au regard de mes objectifs.

                %s
                """.formatted(renderContext(context));
    }

    /**
     * Message pour une question libre.
     *
     * <p>La question de l'utilisateur est clairement delimitee et placee apres
     * les donnees : le cadre reste ainsi celui pose par l'instruction systeme,
     * quoi que contienne la question.
     */
    public String freeQuestionPrompt(CoachContext context, String question) {
        return """
                Voici mes donnees.

                %s

                Ma question, a laquelle tu reponds sans sortir de ton role de coach sportif :
                "%s"
                """.formatted(renderContext(context), question.replace("\"", "'"));
    }

    private String renderContext(CoachContext context) {
        StringJoiner out = new StringJoiner("\n");
        out.add(renderProfile(context));
        out.add("");
        out.add(renderWeek(context));
        out.add("");
        out.add(renderBody(context));
        out.add("");
        out.add(renderSessions(context));
        return out.toString();
    }

    private String renderProfile(CoachContext context) {
        var profile = context.profile();
        StringJoiner out = new StringJoiner("\n");
        out.add("PROFIL");
        out.add("- Objectif principal : " + profile.primaryGoal());
        out.add("- Niveau declare : " + profile.fitnessLevel());
        out.add("- Sports pratiques : " + profile.preferredSports());
        out.add("- Taille : " + profile.heightCm() + " cm, poids : " + profile.currentWeightKg() + " kg");
        if (profile.age() != null) {
            out.add("- Age : " + profile.age() + " ans");
        }
        return out.toString();
    }

    private String renderWeek(CoachContext context) {
        var week = context.week();
        StringJoiner out = new StringJoiner("\n");
        out.add("SEMAINE DU " + week.weekStart() + " AU " + week.weekEnd());
        out.add("- Séances : " + week.sessionCount());
        out.add("- Distance : " + kilometres(week.distanceMeters()));
        out.add("- Temps en mouvement : " + minutes(week.movingDurationSeconds()));
        out.add("- Calories : " + week.caloriesBurned() + " kcal");
        out.add("- Dénivelé positif : " + Math.round(week.elevationGainMeters()) + " m");
        out.add("- Jours consecutifs avec activite : " + week.activeDayStreak());

        var previous = week.previousWeek();
        out.add("- Écart avec la semaine précédente : "
                + signed(previous.sessionCountDelta()) + " séance(s), "
                + signed(previous.distanceMetersDelta() / 1000.0) + " km, "
                + signed(previous.caloriesBurnedDelta()) + " kcal");

        if (week.goals().isEmpty()) {
            out.add("- Aucun objectif fixe.");
        } else {
            out.add("OBJECTIFS EN COURS");
            for (GoalProgressResponse goal : week.goals()) {
                out.add("- " + goal.type() + " : " + goal.currentValue() + " / "
                        + goal.targetValue() + " " + goal.unit()
                        + (goal.completionPercent() == null ? "" : " (" + goal.completionPercent() + " %)")
                        + (goal.achieved() ? " — atteint" : ""));
            }
        }
        return out.toString();
    }

    private String renderBody(CoachContext context) {
        var body = context.body();
        StringJoiner out = new StringJoiner("\n");
        out.add("EVOLUTION PHYSIQUE");
        if (body.checkInCount() == 0) {
            out.add("- Aucune pesée enregistrée. N'invente aucune tendance de poids.");
            return out.toString();
        }
        out.add("- Relevés : " + body.checkInCount());
        out.add("- Poids actuel : " + body.currentWeightKg() + " kg (départ : " + body.startWeightKg() + " kg)");
        if (body.totalChangeKg() != null) {
            out.add("- Variation totale : " + signed(body.totalChangeKg()) + " kg");
        }
        if (body.averageWeeklyChangeKg() != null) {
            out.add("- Rythme moyen : " + signed(body.averageWeeklyChangeKg()) + " kg par semaine");
        }
        out.add("- Tendance : " + body.trend());
        if (body.currentBmi() != null) {
            out.add("- IMC indicatif : " + body.currentBmi());
        }
        return out.toString();
    }

    private String renderSessions(CoachContext context) {
        StringJoiner out = new StringJoiner("\n");
        out.add("SÉANCES RÉCENTES");
        if (context.recentSessions().isEmpty()) {
            out.add("- Aucune séance enregistrée.");
            return out.toString();
        }
        context.recentSessions().stream()
                .limit(RECENT_SESSIONS_IN_PROMPT)
                .forEach(session -> out.add("- " + renderSession(session)));
        return out.toString();
    }

    private String renderSession(WorkoutSummaryResponse session) {
        StringBuilder line = new StringBuilder()
                .append(session.startedAt())
                .append(" — ").append(session.sportType())
                .append(", ").append(kilometres(session.distanceMeters()))
                .append(" en ").append(minutes(session.movingDurationSeconds()));

        if (session.averagePaceSecondsPerKm() != null) {
            line.append(", allure ").append(pace(session.averagePaceSecondsPerKm()));
        }
        line.append(", ").append(session.caloriesBurned()).append(" kcal");
        if (session.perceivedEffort() != null) {
            line.append(", effort percu ").append(session.perceivedEffort()).append("/10");
        }
        if (session.feeling() != null) {
            line.append(", ressenti ").append(session.feeling());
        }
        return line.toString();
    }

    private String kilometres(double meters) {
        return Math.round(meters / 100.0) / 10.0 + " km";
    }

    private String minutes(long seconds) {
        return Math.round(seconds / 60.0) + " min";
    }

    /** Allure au format mm:ss par kilometre, la lecture usuelle en course. */
    private String pace(int secondsPerKm) {
        return "%d:%02d/km".formatted(secondsPerKm / 60, secondsPerKm % 60);
    }

    private String signed(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return (rounded > 0 ? "+" : "") + rounded;
    }

    private String signed(int value) {
        return (value > 0 ? "+" : "") + value;
    }
}
