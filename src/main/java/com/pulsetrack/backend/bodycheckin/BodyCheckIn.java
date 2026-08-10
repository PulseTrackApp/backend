package com.pulsetrack.backend.bodycheckin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Releve physique a une date donnee : poids, mensurations et ressenti.
 *
 * <p>Un releve par jour et par personne (contrainte en base) : deux pesees le
 * meme jour decrivent le meme etat, la seconde corrige la premiere.
 *
 * <p>Porte une {@link LocalDate} et non un instant : une pesee appartient a un
 * jour, pas a une heure precise, et doit rester le meme jour quel que soit le
 * fuseau depuis lequel on la consulte.
 */
@Entity
@Table(name = "body_checkins")
public class BodyCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "waist_cm")
    private Double waistCm;

    @Column(name = "chest_cm")
    private Double chestCm;

    @Column(name = "hips_cm")
    private Double hipsCm;

    /** Energie ressentie de 1 (epuise) a 5 (en pleine forme). */
    @Column(name = "energy_level")
    private Integer energyLevel;

    @Column(name = "average_sleep_hours")
    private Double averageSleepHours;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Requis par JPA. */
    protected BodyCheckIn() {
    }

    public BodyCheckIn(UUID userId, LocalDate checkinDate, Instant createdAt) {
        this.userId = userId;
        this.checkinDate = checkinDate;
        this.createdAt = createdAt;
    }

    /**
     * Applique en bloc les mesures declarees. Comme pour le profil, une seule
     * methode d'ecriture evite les etats partiels qu'une serie de setters
     * laisserait passer.
     */
    public void update(double weightKg,
                       Double waistCm,
                       Double chestCm,
                       Double hipsCm,
                       Integer energyLevel,
                       Double averageSleepHours,
                       String note) {
        this.weightKg = weightKg;
        this.waistCm = waistCm;
        this.chestCm = chestCm;
        this.hipsCm = hipsCm;
        this.energyLevel = energyLevel;
        this.averageSleepHours = averageSleepHours;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getCheckinDate() {
        return checkinDate;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public Double getWaistCm() {
        return waistCm;
    }

    public Double getChestCm() {
        return chestCm;
    }

    public Double getHipsCm() {
        return hipsCm;
    }

    public Integer getEnergyLevel() {
        return energyLevel;
    }

    public Double getAverageSleepHours() {
        return averageSleepHours;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
