package com.pulsetrack.backend.billing;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Une offre du catalogue, telle qu'elle est enregistree.
 *
 * <p>Le catalogue vivait en configuration jusqu'a ce que l'administration doive
 * pouvoir le modifier : un ecran ne peut pas ecrire dans {@code application.yml}.
 * La configuration sert desormais d'amorce au premier demarrage, puis la base
 * fait foi — voir {@link BillingCatalogSeeder}.
 *
 * <p>Le code est la cle primaire, et ce n'est pas une commodite : c'est lui que
 * {@link Subscription#getPlanCode()} reference, et lui qui circule dans l'API.
 * Le renommer casserait les abonnements deja poses, ce que
 * {@link BillingCatalog} refuse explicitement.
 */
@Entity
@Table(name = "billing_plans")
public class BillingPlan {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private BillingProperties.BillingPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false)
    private BillingProperties.Availability availability;

    @Column(name = "highlighted", nullable = false)
    private boolean highlighted;

    /**
     * Avantages, un par ligne.
     *
     * <p>Stockes en un seul texte plutot qu'en table fille : l'ecran
     * d'administration edite exactement cela, un bloc multiligne. Une table pour
     * trois puces se paierait a chaque lecture sans rien apporter.
     */
    @Column(name = "features", nullable = false)
    private String features;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par JPA. */
    protected BillingPlan() {
    }

    public BillingPlan(String code, Instant now) {
        this.code = code;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Applique un etat complet. Tout est remplace d'un coup : l'ecran envoie ce
     * qu'il affiche, et rejouer l'appel ne peut pas produire un autre resultat.
     */
    public void apply(String newName,
                      String newDescription,
                      long newPriceAmount,
                      String newCurrency,
                      BillingProperties.BillingPeriod newPeriod,
                      BillingProperties.Availability newAvailability,
                      boolean newHighlighted,
                      List<String> newFeatures,
                      int newDisplayOrder,
                      Instant now) {
        this.name = newName;
        this.description = newDescription == null ? "" : newDescription;
        this.priceAmount = newPriceAmount;
        this.currency = newCurrency;
        this.period = newPeriod;
        this.availability = newAvailability;
        this.highlighted = newHighlighted;
        this.features = joinFeatures(newFeatures);
        this.displayOrder = newDisplayOrder;
        this.updatedAt = now;
    }

    /**
     * Retire la mise en avant sans toucher au reste.
     *
     * <p>Utilise quand une autre offre la reprend : une seule peut l'etre, et
     * c'est l'index unique partiel de la migration qui le garantit vraiment.
     */
    public void clearHighlight(Instant now) {
        if (!highlighted) {
            return;
        }
        this.highlighted = false;
        this.updatedAt = now;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceAmount() {
        return priceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BillingProperties.BillingPeriod getPeriod() {
        return period;
    }

    public BillingProperties.Availability getAvailability() {
        return availability;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    /** @return les avantages, lignes vides ecartees */
    public List<String> getFeatures() {
        if (features == null || features.isBlank()) {
            return List.of();
        }
        return Arrays.stream(features.split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String joinFeatures(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return lines.stream()
                .map(line -> line == null ? "" : line.strip())
                .filter(line -> !line.isEmpty())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
