package com.pulsetrack.backend.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Point du trace simplifie d'un parcours.
 *
 * <p>Identifiant tire d'une sequence et non de l'identite de la colonne, pour la
 * meme raison que les points GPS : avec une identite, Hibernate doit relire la
 * cle apres chaque insertion et ne peut rien grouper. Un parcours de deux mille
 * points couterait deux mille allers-retours a l'enregistrement.
 *
 * <p>{@code allocationSize} doit rester egal au pas de la sequence declaree dans
 * {@code V11__routes_challenges_achievements.sql} : un ecart entre les deux
 * produirait des cles en double.
 */
@Entity
@Table(name = "saved_route_points")
public class SavedRoutePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "saved_route_points_seq")
    @SequenceGenerator(name = "saved_route_points_seq", sequenceName = "saved_route_points_seq",
            allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SavedRoute route;

    /** Rang dans le trace, a partir de 0. Sans lui, l'ordre du parcours serait perdu. */
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "altitude")
    private Double altitude;

    /**
     * Distance depuis le depart, pour afficher « tu es au km 3,2 du parcours »
     * sans que le client ait a sommer quoi que ce soit.
     *
     * <p>Repartie proportionnellement le long du trace de facon que le dernier
     * point vaille exactement la distance officielle du parcours. Une somme
     * brute des segments, elle, la depasserait — c'est precisement le biais que
     * le filtre de Kalman corrige sur la seance.
     */
    @Column(name = "cumulative_distance_meters", nullable = false)
    private double cumulativeDistanceMeters;

    /** Requis par JPA. */
    protected SavedRoutePoint() {
    }

    SavedRoutePoint(SavedRoute route,
                    int position,
                    double latitude,
                    double longitude,
                    Double altitude,
                    double cumulativeDistanceMeters) {
        this.route = route;
        this.position = position;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.cumulativeDistanceMeters = cumulativeDistanceMeters;
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

    public double getCumulativeDistanceMeters() {
        return cumulativeDistanceMeters;
    }
}
