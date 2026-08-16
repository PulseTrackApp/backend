package com.pulsetrack.backend.workout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.common.domain.SportType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Une seance de sport enregistree, avec ses metriques calculees et son trace.
 *
 * <p>Les metriques derivees (distance, allure, calories...) sont figees a
 * l'enregistrement plutot que recalculees a chaque lecture : l'historique doit
 * rester stable meme si la formule evolue, et l'ecran d'historique n'a alors pas
 * besoin de charger les milliers de points GPS.
 */
@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {

    /**
     * Identifiant assigne par l'application, jamais par la base.
     *
     * <p>C'est ce qui rend l'enregistrement d'une seance rejouable : le mobile
     * choisit l'identifiant avant l'envoi, et un renvoi apres coupure reseau
     * retombe sur la seance deja enregistree au lieu d'en creer une seconde.
     * C'etait l'intention d'origine du schema, restee inappliquee.
     */
    @Id
    private UUID id;

    /** Proprietaire de la seance. Toute lecture filtre dessus. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    /** Duree totale, pauses comprises. */
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    /** Duree hors arrets : c'est elle qui sert aux moyennes et aux calories. */
    @Column(name = "moving_duration_seconds", nullable = false)
    private long movingDurationSeconds;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    /** Nul quand la distance est nulle : une allure n'aurait alors aucun sens. */
    @Column(name = "average_pace_seconds_per_km")
    private Integer averagePaceSecondsPerKm;

    @Column(name = "average_speed_kmh", nullable = false)
    private double averageSpeedKmh;

    @Column(name = "max_speed_kmh", nullable = false)
    private double maxSpeedKmh;

    @Column(name = "elevation_gain_meters", nullable = false)
    private double elevationGainMeters;

    @Column(name = "calories_burned", nullable = false)
    private int caloriesBurned;

    /** Effort percu de 1 a 10, saisi par l'utilisateur. */
    @Column(name = "perceived_effort")
    private Integer perceivedEffort;

    @Enumerated(EnumType.STRING)
    @Column(name = "feeling")
    private Feeling feeling;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Parcours rejoue, quand l'utilisateur a declare reprendre un circuit connu.
     *
     * <p>Identifiant nu et non association : la seance n'a jamais besoin du
     * trace du parcours, et une {@code @ManyToOne} ferait charger un objet entier
     * a chaque lecture d'historique. Le rattachement est purement declaratif, le
     * serveur ne verifie pas que la trace suit reellement le circuit.
     */
    @Column(name = "route_id")
    private UUID routeId;

    /** Defi que cette seance vient regler, le cas echeant. */
    @Column(name = "challenge_id")
    private UUID challengeId;

    /**
     * Le trace appartient a la seance : il est cree et supprime avec elle
     * ({@code orphanRemoval}), et n'a pas d'existence propre dans l'API.
     */
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<GpsPoint> gpsPoints = new ArrayList<>();

    /** Requis par JPA. */
    protected WorkoutSession() {
    }

    public WorkoutSession(UUID id,
                          UUID userId,
                          SportType sportType,
                          Instant startedAt,
                          Instant endedAt,
                          WorkoutMetrics metrics,
                          Integer perceivedEffort,
                          Feeling feeling,
                          String note,
                          Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.sportType = sportType;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = metrics.durationSeconds();
        this.movingDurationSeconds = metrics.movingDurationSeconds();
        this.distanceMeters = metrics.distanceMeters();
        this.averagePaceSecondsPerKm = metrics.averagePaceSecondsPerKm();
        this.averageSpeedKmh = metrics.averageSpeedKmh();
        this.maxSpeedKmh = metrics.maxSpeedKmh();
        this.elevationGainMeters = metrics.elevationGainMeters();
        this.caloriesBurned = metrics.caloriesBurned();
        this.perceivedEffort = perceivedEffort;
        this.feeling = feeling;
        this.note = note;
        this.createdAt = createdAt;
    }

    /**
     * Remplace les metriques figees a l'enregistrement.
     *
     * <p>Reservee au recalcul d'ensemble : les metriques sont calculees une fois
     * pour toutes a la creation, et rien dans le parcours normal ne doit les
     * modifier. Elles ne redeviennent negociables que le jour ou la formule
     * elle-meme est corrigee, et il faut alors pouvoir reparer l'historique
     * plutot que de le laisser mentir.
     */
    void applyMetrics(WorkoutMetrics metrics) {
        this.durationSeconds = metrics.durationSeconds();
        this.movingDurationSeconds = metrics.movingDurationSeconds();
        this.distanceMeters = metrics.distanceMeters();
        this.averagePaceSecondsPerKm = metrics.averagePaceSecondsPerKm();
        this.averageSpeedKmh = metrics.averageSpeedKmh();
        this.maxSpeedKmh = metrics.maxSpeedKmh();
        this.elevationGainMeters = metrics.elevationGainMeters();
        this.caloriesBurned = metrics.caloriesBurned();
    }

    /**
     * Rattache la seance a un parcours rejoue et au defi qu'elle regle.
     *
     * <p>Hors du constructeur parce que les deux sont facultatifs et n'entrent
     * pas dans le calcul des metriques : ajouter deux parametres nullables a une
     * signature qui en compte deja dix aurait rendu chaque appel illisible.
     */
    void attachTo(UUID route, UUID challenge) {
        this.routeId = route;
        this.challengeId = challenge;
    }

    /** Detache la seance d'un parcours supprime, sans rien perdre d'autre. */
    void detachFromRoute() {
        this.routeId = null;
    }

    /**
     * Ajoute un point au trace en maintenant les deux cotes de l'association.
     * Passer par cette methode evite l'oubli classique du cote proprietaire, qui
     * se traduirait par une contrainte {@code not null} violee a l'insertion.
     */
    public void addGpsPoint(int position,
                            double latitude,
                            double longitude,
                            Double altitude,
                            Double accuracy,
                            Double speed,
                            Instant recordedAt) {
        gpsPoints.add(new GpsPoint(this, position, latitude, longitude, altitude, accuracy, speed, recordedAt));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SportType getSportType() {
        return sportType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public long getMovingDurationSeconds() {
        return movingDurationSeconds;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public Integer getAveragePaceSecondsPerKm() {
        return averagePaceSecondsPerKm;
    }

    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public double getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public double getElevationGainMeters() {
        return elevationGainMeters;
    }

    public int getCaloriesBurned() {
        return caloriesBurned;
    }

    public Integer getPerceivedEffort() {
        return perceivedEffort;
    }

    public Feeling getFeeling() {
        return feeling;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    public List<GpsPoint> getGpsPoints() {
        return gpsPoints;
    }
}
