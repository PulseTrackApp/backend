package com.pulsetrack.backend.route;

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
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Un parcours nomme, tire d'une seance passee pour etre repris.
 *
 * <p>C'est le « circuit » : une boucle qu'on refait, dont on veut voir si on
 * l'a couru plus vite que la derniere fois.
 *
 * <p><strong>La distance vient de la seance d'origine</strong>, telle que le
 * filtre de Kalman l'a estimee. Elle n'est jamais recalculee depuis les points
 * conserves : ceux-ci sont bruts et decimes, les resommer ramenerait la
 * surestimation que le filtre corrige.
 *
 * <p>Le parcours survit a la seance qui l'a engendre ({@code on delete set null}
 * en base) : une fois nomme et repris, un circuit a une vie propre.
 */
@Entity
@Table(name = "saved_routes")
public class SavedRoute {

    /** Assigne par l'application, comme pour les seances. */
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type", nullable = false)
    private SportType sportType;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    @Column(name = "elevation_gain_meters", nullable = false)
    private double elevationGainMeters;

    /**
     * Vrai quand l'arrivee est a moins de cent metres du depart. C'est ce qui
     * distingue un circuit d'un aller simple, et cela change le mot employe a
     * l'ecran comme la facon de suivre sa progression.
     */
    @Column(name = "is_loop", nullable = false)
    private boolean loop;

    @Column(name = "point_count", nullable = false)
    private int pointCount;

    /** Seance dont le trace est issu ; nulle si celle-ci a ete supprimee. */
    @Column(name = "source_workout_id")
    private UUID sourceWorkoutId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<SavedRoutePoint> points = new ArrayList<>();

    /** Requis par JPA. */
    protected SavedRoute() {
    }

    public SavedRoute(UUID id,
                      UUID userId,
                      String name,
                      SportType sportType,
                      double distanceMeters,
                      double elevationGainMeters,
                      boolean loop,
                      UUID sourceWorkoutId,
                      Instant now) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.sportType = sportType;
        this.distanceMeters = distanceMeters;
        this.elevationGainMeters = elevationGainMeters;
        this.loop = loop;
        this.sourceWorkoutId = sourceWorkoutId;
        this.pointCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String newName, Instant now) {
        this.name = newName;
        this.updatedAt = now;
    }

    /**
     * Ajoute un point en maintenant les deux cotes de l'association et le
     * compteur. Passer par cette methode evite l'oubli du cote proprietaire, qui
     * se traduirait par une contrainte {@code not null} violee a l'insertion.
     */
    public void addPoint(double latitude, double longitude, Double altitude, double cumulativeDistanceMeters) {
        points.add(new SavedRoutePoint(this, pointCount, latitude, longitude, altitude, cumulativeDistanceMeters));
        pointCount = points.size();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public SportType getSportType() {
        return sportType;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getElevationGainMeters() {
        return elevationGainMeters;
    }

    public boolean isLoop() {
        return loop;
    }

    public int getPointCount() {
        return pointCount;
    }

    public UUID getSourceWorkoutId() {
        return sourceWorkoutId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<SavedRoutePoint> getPoints() {
        return points;
    }
}
