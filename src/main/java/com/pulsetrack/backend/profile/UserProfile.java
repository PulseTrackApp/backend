package com.pulsetrack.backend.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.pulsetrack.backend.bodycheckin.BodyMassIndexCalculator;
import com.pulsetrack.backend.common.domain.SportType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Profil sportif : ce que l'utilisateur declare de lui-meme a l'onboarding, et
 * qui alimente les estimations (calories, IMC, conseils).
 *
 * <p>Reference le compte par son identifiant plutot que par une association JPA :
 * profil et compte sont deux agregats distincts, et cette frontiere evite les
 * chargements en cascade non voulus.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "height_cm", nullable = false)
    private int heightCm;

    @Column(name = "current_weight_kg", nullable = false)
    private double currentWeightKg;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex")
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_goal", nullable = false)
    private PrimaryGoal primaryGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_level", nullable = false)
    private FitnessLevel fitnessLevel;

    @ElementCollection
    @CollectionTable(name = "user_profile_preferred_sports",
            joinColumns = @JoinColumn(name = "user_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sport", nullable = false)
    private Set<SportType> preferredSports = EnumSet.noneOf(SportType.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par JPA. */
    protected UserProfile() {
    }

    public UserProfile(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applique en bloc les valeurs declarees par l'utilisateur. Le profil n'ayant
     * qu'un seul cas d'ecriture (l'enregistrement du formulaire), une methode
     * unique vaut mieux que huit setters qui laisseraient passer un etat partiel.
     */
    public void update(String displayName,
                       int heightCm,
                       double currentWeightKg,
                       LocalDate birthDate,
                       Sex sex,
                       PrimaryGoal primaryGoal,
                       FitnessLevel fitnessLevel,
                       Set<SportType> preferredSports,
                       Instant now) {
        this.displayName = displayName;
        this.heightCm = heightCm;
        this.currentWeightKg = currentWeightKg;
        this.birthDate = birthDate;
        this.sex = sex;
        this.primaryGoal = primaryGoal;
        this.fitnessLevel = fitnessLevel;
        // On vide puis remplit la collection existante : la remplacer par une
        // nouvelle instance ferait perdre le suivi Hibernate.
        this.preferredSports.clear();
        this.preferredSports.addAll(preferredSports);
        this.updatedAt = now;
    }

    /**
     * Aligne le poids courant sur la derniere pesee enregistree.
     *
     * <p>Sans cette synchronisation, un utilisateur qui pese chaque semaine mais
     * ne rouvre jamais son profil verrait ses calories calculees ad vitam avec
     * le poids saisi a l'inscription.
     */
    public void applyMeasuredWeight(double weightKg, Instant now) {
        if (this.currentWeightKg == weightKg) {
            return;
        }
        this.currentWeightKg = weightKg;
        this.updatedAt = now;
    }

    /**
     * @return indice de masse corporelle, ou {@code null} si la taille n'est pas
     *         encore renseignee
     */
    public Double bodyMassIndex() {
        return BodyMassIndexCalculator.calculate(currentWeightKg, heightCm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getHeightCm() {
        return heightCm;
    }

    public double getCurrentWeightKg() {
        return currentWeightKg;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Sex getSex() {
        return sex;
    }

    public PrimaryGoal getPrimaryGoal() {
        return primaryGoal;
    }

    public FitnessLevel getFitnessLevel() {
        return fitnessLevel;
    }

    public Set<SportType> getPreferredSports() {
        return preferredSports;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
