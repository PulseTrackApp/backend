package com.pulsetrack.backend.workout;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Point du trace GPS d'une seance.
 *
 * <p>Identifiant numerique auto-incremente et non UUID : ces lignes se comptent
 * par milliers et ne sont jamais adressees individuellement par l'API.
 */
@Entity
@Table(name = "gps_points")
public class GpsPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WorkoutSession session;

    /** Rang dans le trace, a partir de 0. Sans lui, l'ordre du parcours serait perdu. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    /** Altitude en metres, absente si le telephone ne la fournit pas. */
    @Column(name = "altitude")
    private Double altitude;

    /** Precision horizontale annoncee par le GPS, en metres. */
    @Column(name = "accuracy")
    private Double accuracy;

    /** Vitesse instantanee en m/s telle que mesuree par le capteur. */
    @Column(name = "speed")
    private Double speed;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Requis par JPA. */
    protected GpsPoint() {
    }

    public GpsPoint(WorkoutSession session,
                    int position,
                    double latitude,
                    double longitude,
                    Double altitude,
                    Double accuracy,
                    Double speed,
                    Instant recordedAt) {
        this.session = session;
        this.position = position;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public Double getSpeed() {
        return speed;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
