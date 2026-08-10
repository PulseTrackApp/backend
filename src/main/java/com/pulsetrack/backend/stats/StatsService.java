package com.pulsetrack.backend.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyCheckInService;
import com.pulsetrack.backend.stats.dto.StatsResponse;
import com.pulsetrack.backend.stats.dto.StatsTotals;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;
import com.pulsetrack.backend.workout.WorkoutStatsRow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistiques sportives et corporelles sur une periode au choix.
 *
 * <p>Un seul endpoint pour la semaine, le mois, l'annee et l'integralite de
 * l'historique : le calcul est identique, seules les bornes changent. Ajouter un
 * trimestre demanderait une valeur d'enum, rien de plus.
 */
@Service
public class StatsService {

    private final WorkoutSessionRepository sessions;
    private final BodyCheckInService bodyCheckInService;
    private final StatsAggregator aggregator;

    public StatsService(WorkoutSessionRepository sessions,
                        BodyCheckInService bodyCheckInService,
                        StatsAggregator aggregator) {
        this.sessions = sessions;
        this.bodyCheckInService = bodyCheckInService;
        this.aggregator = aggregator;
    }

    /**
     * @param period    fenetre d'analyse
     * @param reference jour quelconque de la periode voulue ; par defaut aujourd'hui
     * @param zone      fuseau de l'utilisateur, qui decide des bornes de journee
     */
    @Transactional(readOnly = true)
    public StatsResponse compute(UUID userId, StatsPeriod period, LocalDate reference, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate anchor = reference == null ? today : reference;

        LocalDate start = period == StatsPeriod.LIFETIME
                ? firstEverActivityDate(userId, zone, today)
                : period.startOf(anchor);
        LocalDate end = period == StatsPeriod.LIFETIME ? today : period.endOf(start);

        List<WorkoutStatsRow> rows = rowsBetween(userId, start, end, zone);

        return new StatsResponse(
                period,
                start,
                end,
                zone.getId(),
                aggregator.totalsOf(rows, zone),
                previousTotals(userId, period, start, zone),
                aggregator.breakdownBySport(rows, zone),
                aggregator.seriesOf(rows, start, end, period.bucketSize(), zone),
                aggregator.recordsOf(rows, zone),
                bodyCheckInService.statsBetween(userId, start, end));
    }

    /**
     * Totaux de la periode precedente, pour situer la periode courante.
     *
     * @return {@code null} pour {@code LIFETIME}
     */
    private StatsTotals previousTotals(UUID userId, StatsPeriod period, LocalDate start, ZoneId zone) {
        LocalDate previousStart = period.previousStartOf(start);
        if (previousStart == null) {
            return null;
        }
        LocalDate previousEnd = period.endOf(previousStart);
        return aggregator.totalsOf(rowsBetween(userId, previousStart, previousEnd, zone), zone);
    }

    /**
     * Charge les seances de {@code [start, end]}.
     *
     * <p>La borne haute est convertie en debut du jour suivant : la requete
     * travaille sur des instants, et {@code < lendemain 00h00} inclut bien toute
     * la journee du dernier jour.
     */
    private List<WorkoutStatsRow> rowsBetween(UUID userId, LocalDate start, LocalDate end, ZoneId zone) {
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(zone).toInstant();
        return sessions.statsRowsBetween(userId, from, to);
    }

    /**
     * Date de la premiere seance jamais enregistree.
     *
     * @return aujourd'hui si l'historique est vide, ce qui produit une periode
     *         d'un jour et des totaux a zero plutot qu'une erreur
     */
    private LocalDate firstEverActivityDate(UUID userId, ZoneId zone, LocalDate today) {
        Instant first = sessions.findFirstStartedAt(userId);
        return first == null ? today : first.atZone(zone).toLocalDate();
    }
}
