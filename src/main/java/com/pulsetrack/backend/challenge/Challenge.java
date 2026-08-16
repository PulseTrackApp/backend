package com.pulsetrack.backend.challenge;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un defi que l'utilisateur se pose : couvrir une distance dans un temps donne.
 *
 * <p>Deux echeances a ne pas confondre :
 * <ul>
 *   <li>{@code deadlineAt} — la fin du chronometre, {@code startedAt} plus la
 *       duree cible. C'est celle qui donne les alertes pendant l'effort ;</li>
 *   <li>{@code expiresOn} — la date limite pour <em>tenter</em> le defi, qui
 *       donne les rappels par notification. Facultative.</li>
 * </ul>
 *
 * <p>{@code deadlineAt} est figee au depart plutot que recalculee a chaque
 * lecture : une borne qui se recalcule glisserait si la duree cible etait
 * modifiee en cours de route, et l'utilisateur verrait son echeance bouger.
 */
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Column(name = "target_distance_meters", nullable = false)
    private double targetDistanceMeters;

    @Column(name = "target_duration_seconds", nullable = false)
    private long targetDurationSeconds;

    /**
     * Defi pose sur un circuit connu. Le parcours peut etre supprime sans que le
     * defi perde son sens : distance et duree suffisent a le juger.
     */
    @Column(name = "route_id")
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChallengeStatus status;

    /** Date limite pour tenter le defi. Nulle pour un defi sans peremption. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "workout_id")
    private UUID workoutId;

    @Column(name = "achieved_distance_meters")
    private Double achievedDistanceMeters;

    @Column(name = "achieved_duration_seconds")
    private Long achievedDurationSeconds;

    @Column(name = "succeeded")
    private Boolean succeeded;

    /** Requis par JPA. */
    protected Challenge() {
    }

    public Challenge(UUID id,
                     UUID userId,
                     String title,
                     SportType sportType,
                     double targetDistanceMeters,
                     long targetDurationSeconds,
                     UUID routeId,
                     LocalDate expiresOn,
                     Instant now) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.sportType = sportType;
        this.targetDistanceMeters = targetDistanceMeters;
        this.targetDurationSeconds = targetDurationSeconds;
        this.routeId = routeId;
        this.expiresOn = expiresOn;
        this.status = ChallengeStatus.DRAFT;
        this.createdAt = now;
    }

    /** Arme le chronometre et pose l'echeance. */
    void start(Instant now) {
        this.status = ChallengeStatus.ACTIVE;
        this.startedAt = now;
        this.deadlineAt = now.plusSeconds(targetDurationSeconds);
    }

    /**
     * Fige le resultat.
     *
     * @param succeededNow verdict, calcule ailleurs : l'entite enregistre, elle
     *                     ne juge pas
     */
    void settle(boolean succeededNow,
                double distanceMeters,
                long durationSeconds,
                UUID settlingWorkoutId,
                Instant now) {
        this.status = succeededNow ? ChallengeStatus.SUCCEEDED : ChallengeStatus.FAILED;
        this.succeeded = succeededNow;
        this.achievedDistanceMeters = distanceMeters;
        this.achievedDurationSeconds = durationSeconds;
        this.workoutId = settlingWorkoutId;
        this.completedAt = now;
    }

    void abandon(Instant now) {
        this.status = ChallengeStatus.ABANDONED;
        this.completedAt = now;
    }

    void expire(Instant now) {
        this.status = ChallengeStatus.EXPIRED;
        this.completedAt = now;
    }

    /** Detache le defi d'une seance supprimee, sans effacer le resultat obtenu. */
    void detachFromWorkout() {
        this.workoutId = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public SportType getSportType() {
        return sportType;
    }

    public double getTargetDistanceMeters() {
        return targetDistanceMeters;
    }

    public long getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public ChallengeStatus getStatus() {
        return status;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getWorkoutId() {
        return workoutId;
    }

    public Double getAchievedDistanceMeters() {
        return achievedDistanceMeters;
    }

    public Long getAchievedDurationSeconds() {
        return achievedDurationSeconds;
    }

    public Boolean getSucceeded() {
        return succeeded;
    }

    /**
     * Allure qu'il faut tenir pour reussir, en secondes par kilometre. C'est le
     * chiffre que l'utilisateur regarde avant de partir.
     */
    public int requiredPaceSecondsPerKm() {
        return (int) Math.round(targetDurationSeconds / (targetDistanceMeters / 1_000d));
    }

    /** Vitesse a tenir, en km/h — plus parlante que l'allure a velo. */
    public double requiredSpeedKmh() {
        return targetDistanceMeters / 1_000d / (targetDurationSeconds / 3_600d);
    }
}
