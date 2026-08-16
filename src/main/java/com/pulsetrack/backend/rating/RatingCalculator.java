package com.pulsetrack.backend.rating;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.pulsetrack.backend.goal.Goal;
import com.pulsetrack.backend.goal.GoalType;
import com.pulsetrack.backend.motivation.Wording;
import com.pulsetrack.backend.rating.dto.RatingResponse;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.springframework.stereotype.Component;

/**
 * Calcule la note d'un utilisateur sur quatre semaines.
 *
 * <p><strong>Ce qu'on note, et pourquoi dans cet ordre.</strong> La regularite
 * pese le plus lourd : c'est elle qui fait progresser et c'est elle qui tient
 * dans la duree. Noter d'abord les kilometres reviendrait a feliciter la sortie
 * heroique du dimanche qui n'est jamais suivie d'une deuxieme.
 *
 * <p><strong>Deterministe et local.</strong> Aucun appel a l'assistant : une note
 * ne doit rien couter, ne dependre d'aucune cle tierce, et rendre la meme valeur
 * deux fois le meme jour.
 *
 * <p>Classe sans etat ni acces a la base : elle recoit des seances deja lues et
 * rend un objet. Chaque bareme s'eprouve avec des chiffres choisis.
 */
@Component
public class RatingCalculator {

    /** Fenetre d'analyse. Quatre semaines : assez pour lisser, assez court pour reagir. */
    public static final int WINDOW_DAYS = 28;

    /**
     * Reference de regularite : quatre jours actifs par semaine valent 100. Ce
     * n'est pas un maximum theorique mais un rythme reellement tenable — un
     * bareme qui exige sept jours sur sept ne recompense jamais personne.
     */
    private static final double REGULARITY_TARGET_ACTIVE_DAYS = 16;

    /**
     * Reference de volume : 150 minutes d'activite par semaine, soit 600 sur la
     * fenetre. C'est la recommandation de l'Organisation mondiale de la sante, et
     * non un chiffre invente pour l'occasion.
     */
    private static final double VOLUME_TARGET_MINUTES = 600;

    /** Hausse de volume qui vaut la note maximale de progression. */
    private static final double PROGRESSION_FULL_GROWTH = 0.20;

    /** En dessous de cette baisse, la progression tombe au plancher. */
    private static final double PROGRESSION_FLOOR_DROP = 0.40;

    /**
     * Note rendue en l'absence de reference. Ni recompense ni sanction : on ne
     * sait pas, et la moyenne est le seul chiffre honnete.
     */
    private static final int NEUTRAL_SCORE = 50;

    /**
     * Elements d'entree du calcul, tous deja lus en base.
     *
     * @param window       seances des vingt-huit derniers jours
     * @param previous     seances des vingt-huit jours precedents, pour la
     *                     progression
     * @param activeDays   jours distincts avec au moins une seance dans la fenetre
     * @param weeklyGoals  objectifs hebdomadaires actifs ; vide s'il n'y en a pas,
     *                     auquel cas la composante correspondante est retiree et
     *                     les autres renormalisees
     * @param streakDays   jours consecutifs avec au moins une seance
     */
    public record Inputs(
            List<WorkoutStatsRow> window,
            List<WorkoutStatsRow> previous,
            int activeDays,
            List<Goal> weeklyGoals,
            int streakDays) {
    }

    /**
     * @param today jour de reference dans le fuseau de l'utilisateur
     * @param zone  fuseau, qui decide ou tombent les bornes de journee
     */
    public RatingResponse rate(Inputs current, Integer previousScore, LocalDate today, ZoneId zone) {
        if (current.window().isEmpty() && current.previous().isEmpty()) {
            return welcome(today);
        }

        Map<RatingComponent, RatingResponse.Component> components = componentsOf(current, today, zone);
        int score = weightedScore(components.values());

        RatingTier tier = RatingTier.of(score);
        RatingTier next = tier.next();
        Integer toNext = next == tier ? null : Math.max(0, next.minimumScore() - score);

        return new RatingResponse(
                score,
                RatingTier.gradeOf(score),
                tier,
                tier.title(),
                messageOf(current, tier),
                adviceOf(components, toNext, next),
                WINDOW_DAYS,
                today,
                current.streakDays(),
                next,
                toNext,
                trendOf(score, previousScore),
                List.copyOf(components.values()));
    }

    /**
     * Note d'une fenetre seule, sans message ni palier : c'est ce qui sert de
     * point de comparaison pour la tendance.
     */
    public int scoreOf(Inputs inputs, LocalDate today, ZoneId zone) {
        if (inputs.window().isEmpty() && inputs.previous().isEmpty()) {
            return 0;
        }
        return weightedScore(componentsOf(inputs, today, zone).values());
    }

    private Map<RatingComponent, RatingResponse.Component> componentsOf(Inputs inputs,
                                                                        LocalDate today,
                                                                        ZoneId zone) {
        Map<RatingComponent, RatingResponse.Component> components =
                new EnumMap<>(RatingComponent.class);

        components.put(RatingComponent.REGULARITY, regularity(inputs.activeDays()));
        components.put(RatingComponent.VOLUME, volume(inputs.window()));
        if (!inputs.weeklyGoals().isEmpty()) {
            components.put(RatingComponent.GOALS, goals(inputs, today, zone));
        }
        components.put(RatingComponent.PROGRESSION, progression(inputs.window(), inputs.previous()));

        return components;
    }

    private RatingResponse.Component regularity(int activeDays) {
        int score = boundedScore(activeDays / REGULARITY_TARGET_ACTIVE_DAYS * 100);
        return component(RatingComponent.REGULARITY, score,
                "%s sur %d jours.".formatted(
                        Wording.plural(activeDays, "jour actif", "jours actifs"), WINDOW_DAYS));
    }

    private RatingResponse.Component volume(List<WorkoutStatsRow> window) {
        long seconds = window.stream().mapToLong(WorkoutStatsRow::movingDurationSeconds).sum();
        double meters = window.stream().mapToDouble(WorkoutStatsRow::distanceMeters).sum();
        int score = boundedScore(seconds / 60d / VOLUME_TARGET_MINUTES * 100);

        return component(RatingComponent.VOLUME, score,
                "%s en mouvement, %s parcourus.".formatted(Wording.duration(seconds), Wording.distance(meters)));
    }

    /**
     * Part des objectifs hebdomadaires tenus sur les quatre dernieres periodes de
     * sept jours.
     *
     * <p>Periodes glissantes qui se terminent aujourd'hui, et non semaines
     * calendaires : la note se lit un mercredi comme un dimanche, et une semaine
     * en cours a moitie faite ne viendrait pas fausser le compte.
     */
    private RatingResponse.Component goals(Inputs inputs, LocalDate today, ZoneId zone) {
        List<Goal> goals = inputs.weeklyGoals();
        int periods = WINDOW_DAYS / 7;
        int met = 0;
        int total = 0;

        for (int period = 0; period < periods; period++) {
            LocalDate to = today.minusDays(7L * period);
            LocalDate from = to.minusDays(7);
            List<WorkoutStatsRow> rows = between(inputs.window(), from, to, zone);

            for (Goal goal : goals) {
                total++;
                if (achievedValue(goal.getType(), rows) >= goal.getTargetValue()) {
                    met++;
                }
            }
        }

        int score = total == 0 ? NEUTRAL_SCORE : boundedScore(met / (double) total * 100);
        return component(RatingComponent.GOALS, score,
                total == 0
                        ? "Pas encore d'objectif hebdomadaire mesurable."
                        : "%d objectifs hebdomadaires tenus sur %d.".formatted(met, total));
    }

    /**
     * Volume de la fenetre compare aux vingt-huit jours precedents.
     *
     * <p>Sans reference — quelqu'un qui vient de commencer — on rend la note
     * neutre plutot que zero : ne pas avoir progresse faute de passe n'est pas un
     * defaut.
     */
    private RatingResponse.Component progression(List<WorkoutStatsRow> window, List<WorkoutStatsRow> previous) {
        long now = window.stream().mapToLong(WorkoutStatsRow::movingDurationSeconds).sum();
        long before = previous.stream().mapToLong(WorkoutStatsRow::movingDurationSeconds).sum();

        if (before == 0) {
            return component(RatingComponent.PROGRESSION, now > 0 ? 70 : NEUTRAL_SCORE,
                    now > 0
                            ? "Premiere periode mesurable : rien a quoi se comparer encore."
                            : "Pas encore de reference de progression.");
        }

        double growth = (now - before) / (double) before;
        int score = growthScore(growth);
        String comment = describeGrowth(growth, now, before);
        return component(RatingComponent.PROGRESSION, score, comment);
    }

    /**
     * Bareme de progression, lineaire par morceaux : 100 a partir de +20 %, la
     * note neutre a volume constant, le plancher a partir de -40 %.
     */
    private int growthScore(double growth) {
        if (growth >= PROGRESSION_FULL_GROWTH) {
            return 100;
        }
        if (growth >= 0) {
            return boundedScore(70 + growth / PROGRESSION_FULL_GROWTH * 30);
        }
        if (growth <= -PROGRESSION_FLOOR_DROP) {
            return 20;
        }
        return boundedScore(70 + growth / PROGRESSION_FLOOR_DROP * 50);
    }

    private String describeGrowth(double growth, long now, long before) {
        if (Math.abs(growth) < 0.05) {
            return "Volume stable par rapport aux quatre semaines precedentes.";
        }
        return growth > 0
                ? "%s de plus qu'il y a quatre semaines.".formatted(Wording.duration(now - before))
                : "%s de moins qu'il y a quatre semaines.".formatted(Wording.duration(before - now));
    }

    /**
     * Moyenne ponderee, renormalisee sur les composantes reellement mesurables.
     *
     * <p>Sans renormalisation, un compte sans objectif verrait sa note plafonnee
     * a 75 sans qu'aucun ecran ne lui dise pourquoi.
     */
    private int weightedScore(java.util.Collection<RatingResponse.Component> components) {
        int totalWeight = components.stream().mapToInt(RatingResponse.Component::weight).sum();
        if (totalWeight == 0) {
            return 0;
        }
        double sum = components.stream()
                .mapToDouble(one -> one.score() * (double) one.weight())
                .sum();
        return boundedScore(sum / totalWeight);
    }

    private RatingResponse.Trend trendOf(int score, Integer previousScore) {
        if (previousScore == null) {
            return new RatingResponse.Trend(null, null, RatingResponse.Trend.Direction.FLAT);
        }
        int delta = score - previousScore;
        RatingResponse.Trend.Direction direction = delta > 2
                ? RatingResponse.Trend.Direction.UP
                : delta < -2 ? RatingResponse.Trend.Direction.DOWN : RatingResponse.Trend.Direction.FLAT;
        return new RatingResponse.Trend(previousScore, delta, direction);
    }

    private String messageOf(Inputs inputs, RatingTier tier) {
        int sessions = inputs.window().size();
        String volume = Wording.distance(
                inputs.window().stream().mapToDouble(WorkoutStatsRow::distanceMeters).sum());

        String base = "%s et %s sur quatre semaines.".formatted(
                Wording.plural(sessions, "seance", "seances"), volume);

        return switch (tier) {
            case ATHLETE -> base + " Ce niveau de regularite est rare, tiens-le.";
            case STRONG -> base + " La regularite est installee.";
            case SOLID -> base + " Le rythme est la, il ne demande qu'a s'installer.";
            case REGULAR -> base + " C'est la regularite qui paie, et elle commence a se voir.";
            case STARTING, NEW -> base + " Le plus dur est fait : tu as commence.";
        };
    }

    /**
     * Une seule action, celle qui rapporte le plus : la composante la plus faible
     * est aussi celle ou l'effort se voit le plus vite. Trois conseils se lisent
     * et s'oublient, un seul se suit.
     */
    private String adviceOf(Map<RatingComponent, RatingResponse.Component> components,
                            Integer toNext,
                            RatingTier next) {
        RatingResponse.Component weakest = components.values().stream()
                .min((left, right) -> Integer.compare(left.score(), right.score()))
                .orElse(null);
        if (weakest == null) {
            return null;
        }

        String action = switch (weakest.key()) {
            case REGULARITY -> "Vise une sortie de plus par semaine, meme courte : c'est ce qui compte le plus.";
            case VOLUME -> "Allonge une sortie de dix minutes plutot que d'en ajouter une.";
            case GOALS -> "Ton objectif hebdomadaire est peut-etre trop haut : baisse-le d'un cran et tiens-le.";
            case PROGRESSION -> "Reprends simplement le volume des quatre semaines precedentes.";
        };

        return toNext == null || toNext == 0
                ? action
                : action + " Il te manque %d points pour le palier %s.".formatted(toNext, next.title());
    }

    /** Accueil d'un compte sans aucune seance. */
    private RatingResponse welcome(LocalDate today) {
        return new RatingResponse(
                null,
                null,
                RatingTier.NEW,
                null,
                "Aucune seance enregistree pour l'instant. La note apparaitra des la premiere.",
                "Enregistre une premiere sortie, meme une marche de vingt minutes.",
                WINDOW_DAYS,
                today,
                0,
                RatingTier.STARTING,
                null,
                new RatingResponse.Trend(null, null, RatingResponse.Trend.Direction.FLAT),
                List.of());
    }

    /**
     * Seances des sept jours qui se terminent a {@code to}, celui-ci inclus.
     *
     * <p>Borne basse exclue et borne haute incluse : deux periodes consecutives
     * ne peuvent pas compter deux fois la meme journee.
     */
    private List<WorkoutStatsRow> between(List<WorkoutStatsRow> rows, LocalDate from, LocalDate to, ZoneId zone) {
        return rows.stream()
                .filter(row -> {
                    LocalDate day = row.startedAt().atZone(zone).toLocalDate();
                    return day.isAfter(from) && !day.isAfter(to);
                })
                .toList();
    }

    private double achievedValue(GoalType type, List<WorkoutStatsRow> rows) {
        return switch (type) {
            case WEEKLY_DISTANCE -> rows.stream().mapToDouble(WorkoutStatsRow::distanceMeters).sum() / 1_000d;
            case WEEKLY_SESSIONS -> rows.size();
            case WEEKLY_DURATION -> rows.stream().mapToLong(WorkoutStatsRow::movingDurationSeconds).sum() / 60d;
            case WEEKLY_CALORIES -> rows.stream().mapToInt(WorkoutStatsRow::caloriesBurned).sum();
            // Un poids cible n'est pas un cumul hebdomadaire : il n'a rien a faire
            // dans cette composante, et l'appelant l'a deja ecarte.
            case TARGET_WEIGHT -> 0;
        };
    }

    private RatingResponse.Component component(RatingComponent key, int score, String comment) {
        return new RatingResponse.Component(key, key.label(), score, key.weight(), comment);
    }

    private int boundedScore(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }
}
