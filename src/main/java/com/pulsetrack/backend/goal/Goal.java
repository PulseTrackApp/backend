package com.pulsetrack.backend.goal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Objectif que l'utilisateur se fixe.
 *
 * <p>Un seul objectif actif par type et par personne, garanti par un index
 * partiel en base. Archiver ({@code active = false}) plutot que supprimer
 * conserve l'historique de ce qu'on s'etait fixe.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GoalType type;

    /** Exprimee dans l'unite du type : km, minutes, seances, kcal ou kg. */
    @Column(name = "target_value", nullable = false)
    private double targetValue;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Nulle pour un objectif sans echeance. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par JPA. */
    protected Goal() {
    }

    public Goal(UUID userId, GoalType type, Instant now) {
        this.userId = userId;
        this.type = type;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(double targetValue, LocalDate startDate, LocalDate endDate, Instant now) {
        this.targetValue = targetValue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    /**
     * Retire l'objectif de la course sans effacer sa trace. Libere aussi la
     * contrainte d'unicite, ce qui permet d'en fixer un nouveau du meme type.
     */
    public void archive(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public GoalType getType() {
        return type;
    }

    public double getTargetValue() {
        return targetValue;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
