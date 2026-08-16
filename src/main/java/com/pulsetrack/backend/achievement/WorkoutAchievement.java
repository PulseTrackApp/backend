package com.pulsetrack.backend.achievement;

import java.time.Instant;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Trace qu'un record est tombe lors d'une seance donnee.
 *
 * <p><strong>Un evenement, pas un etat.</strong> Le record <em>courant</em> se
 * recalcule a la lecture ({@link SportBests}) parce qu'il change quand une seance
 * est supprimee. Le fait qu'une seance ait battu un record le 15 aout, lui, reste
 * vrai pour toujours.
 *
 * <p>Deux raisons concretes de le conserver plutot que de le recalculer :
 * <ul>
 *   <li>le renvoi d'une seance deja enregistree, apres une coupure reseau en fin
 *       de course, doit rendre <em>exactement</em> la meme liste — sinon les
 *       felicitations explosent deux fois, ou pas du tout ;</li>
 *   <li>l'ecran d'historique peut poser un badge sur les seances remarquables
 *       sans rejouer la chronologie de tous les records a chaque affichage.</li>
 * </ul>
 *
 * <p>La valeur est stockee telle qu'elle etait au moment du record, dans l'unite
 * de {@link AchievementKind#unit()}.
 */
@Entity
@Table(name = "workout_achievements")
public class WorkoutAchievement {

    @Id
    private UUID id;

    @Column(name = "workout_id", nullable = false)
    private UUID workoutId;

    /**
     * Duplique depuis la seance. Toutes les lectures filtrent sur le
     * proprietaire, et une jointure de plus a chaque affichage d'historique
     * serait payee pour rien.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AchievementKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Column(name = "unit", nullable = false)
    private String unit;

    /** Nul pour un premier evenement, qui n'a pas de precedent a depasser. */
    @Column(name = "previous_value")
    private Double previousValue;

    @Column(name = "new_value", nullable = false)
    private double newValue;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    /** Requis par JPA. */
    protected WorkoutAchievement() {
    }

    public WorkoutAchievement(UUID workoutId, UUID userId, AchievementDetector.Detected detected) {
        this.id = UUID.randomUUID();
        this.workoutId = workoutId;
        this.userId = userId;
        this.kind = detected.kind();
        this.sportType = detected.sportType();
        this.unit = detected.kind().unit();
        this.previousValue = detected.previousValue();
        this.newValue = detected.newValue();
        this.achievedAt = detected.achievedAt();
    }

    /** Reconstitue le verdict d'origine, pour reformer le message a la lecture. */
    public AchievementDetector.Detected asDetected() {
        return new AchievementDetector.Detected(kind, sportType, previousValue, newValue, achievedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkoutId() {
        return workoutId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AchievementKind getKind() {
        return kind;
    }

    public SportType getSportType() {
        return sportType;
    }

    public String getUnit() {
        return unit;
    }

    public Double getPreviousValue() {
        return previousValue;
    }

    public double getNewValue() {
        return newValue;
    }

    public Instant getAchievedAt() {
        return achievedAt;
    }
}
