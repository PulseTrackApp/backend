package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acces aux seances.
 *
 * <p>Toutes les methodes portent {@code userId} dans leur critere. Ce n'est pas
 * un detail de confort : c'est ce qui rend impossible, par construction, de lire
 * ou supprimer la seance d'un autre utilisateur en devinant son identifiant.
 */
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    /** Detail d'une seance : le trace est charge dans la meme requete. */
    @EntityGraph(attributePaths = "gpsPoints")
    Optional<WorkoutSession> findByIdAndUserId(UUID id, UUID userId);

    /** Historique pagine, sans le trace. */
    Page<WorkoutSession> findByUserId(UUID userId, Pageable pageable);

    /**
     * Toutes les seances avec leur trace, pour l'export.
     *
     * <p>Volontairement non paginee : une archive partielle n'aurait aucune
     * valeur. C'est aussi la seule requete du projet dont le volume n'est pas
     * borne — a l'echelle d'un suivi personnel elle reste raisonnable, mais c'est
     * ici qu'il faudrait streamer si l'historique devenait enorme.
     */
    @EntityGraph(attributePaths = "gpsPoints")
    List<WorkoutSession> findByUserIdOrderByStartedAtAsc(UUID userId);

    /** Historique pagine filtre par sport. */
    Page<WorkoutSession> findByUserIdAndSportType(UUID userId, SportType sportType, Pageable pageable);

    /**
     * Totaux d'une periode, calcules par la base.
     *
     * <p>Borne haute exclue : deux semaines consecutives ne peuvent pas compter
     * deux fois la meme seance.
     */
    @Query("""
            select new com.pulsetrack.backend.workout.WorkoutTotals(
                count(w),
                sum(w.distanceMeters),
                sum(w.movingDurationSeconds),
                sum(w.caloriesBurned),
                sum(w.elevationGainMeters))
            from WorkoutSession w
            where w.userId = :userId
              and w.startedAt >= :from
              and w.startedAt < :to
            """)
    WorkoutTotals totalsBetween(@Param("userId") UUID userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /**
     * Seances d'une periode, en projection legere, pour les statistiques.
     *
     * <p>Bornes {@code [from, to[} : deux periodes consecutives ne comptent
     * jamais deux fois la meme seance.
     */
    @Query("""
            select new com.pulsetrack.backend.workout.WorkoutStatsRow(
                w.startedAt, w.sportType, w.distanceMeters, w.movingDurationSeconds,
                w.caloriesBurned, w.elevationGainMeters, w.averagePaceSecondsPerKm)
            from WorkoutSession w
            where w.userId = :userId
              and w.startedAt >= :from
              and w.startedAt < :to
            order by w.startedAt asc
            """)
    List<WorkoutStatsRow> statsRowsBetween(@Param("userId") UUID userId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    /**
     * Date de la toute premiere seance, point de depart de la periode
     * « depuis le debut ».
     */
    @Query("select min(w.startedAt) from WorkoutSession w where w.userId = :userId")
    Instant findFirstStartedAt(@Param("userId") UUID userId);

    /** Fiche d'un compte dans l'administration : depuis quand il ne s'entraine plus. */
    @Query("select max(w.startedAt) from WorkoutSession w where w.userId = :userId")
    Instant findLastStartedAt(@Param("userId") UUID userId);

    long countByUserId(UUID userId);

    /**
     * Comptes ayant enregistre au moins une seance depuis une date, pour le
     * tableau de bord. {@code count(distinct)} en base plutot qu'un chargement
     * des seances suivi d'un regroupement en memoire : la difference se voit des
     * le premier millier de lignes.
     */
    @Query("select count(distinct w.userId) from WorkoutSession w where w.startedAt >= :from")
    long countActiveUsersSince(@Param("from") Instant from);

    long countByStartedAtGreaterThanEqual(Instant from);

    /**
     * Jours distincts comportant au moins une seance, du plus recent au plus
     * ancien, pour reconstituer la serie d'activite.
     *
     * <p>Requete native : le decoupage en jours depend du fuseau de
     * l'utilisateur, et {@code at time zone} n'a pas d'equivalent en JPQL. Une
     * course commencee a 00h30 a Ouagadougou doit compter pour ce jour-la, pas
     * pour la veille en UTC.
     *
     * <p>On ecrit {@code cast(... as date)} et non {@code ...::date} : Hibernate
     * interpreterait le {@code ::} comme le debut d'un parametre nomme.
     */
    @Query(value = """
            select distinct cast(started_at at time zone :zone as date) as active_day
            from workout_sessions
            where user_id = :userId
              and started_at >= :from
            order by active_day desc
            """, nativeQuery = true)
    List<LocalDate> activeDaysSince(@Param("userId") UUID userId,
                                    @Param("from") Instant from,
                                    @Param("zone") String zone);
}
