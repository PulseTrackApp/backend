package com.pulsetrack.backend.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Droit d'usage enregistre pour un compte.
 *
 * <p><strong>L'absence de ligne est le cas normal</strong>, et elle a un sens
 * precis : le compte est dans sa periode d'essai, comptee depuis sa creation.
 * C'est ce qui evite d'avoir a creer une ligne pour chaque inscription, et
 * surtout d'avoir a en fabriquer retroactivement pour tous les comptes existants
 * le jour ou la facturation arrive.
 *
 * <p>Une ligne n'apparait donc que lorsqu'un droit est <em>accorde ou retire a la
 * main</em> : abonnement encaisse, compte offert, compte suspendu. Le jour ou un
 * encaissement automatique existera, c'est lui qui l'ecrira.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    /** Offre souscrite ; nulle pour un compte simplement suspendu. */
    @Column(name = "plan_code")
    private String planCode;

    /**
     * Dernier jour <strong>inclus</strong> de validite. Nul pour un droit sans
     * echeance — un compte offert a vie, ou une suspension.
     */
    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    /** Trace de qui a decide, pour qu'un droit accorde reste explicable. */
    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par JPA. */
    protected Subscription() {
    }

    public Subscription(UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.status = SubscriptionStatus.NONE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void apply(SubscriptionStatus newStatus,
                      String newPlanCode,
                      LocalDate newPeriodEnd,
                      String newNote,
                      Instant now) {
        this.status = newStatus;
        this.planCode = newPlanCode;
        this.currentPeriodEnd = newPeriodEnd;
        this.note = newNote;
        this.updatedAt = now;
    }

    /**
     * Etat reel a une date donnee.
     *
     * <p>Un abonnement actif dont l'echeance est passee n'est pas actif : le
     * calculer plutot que de le stocker evite d'avoir a faire tourner une tache
     * de peremption, et surtout evite qu'un compte reste ouvert parce que cette
     * tache n'a pas tourne.
     */
    public SubscriptionStatus effectiveStatusOn(LocalDate day) {
        if (status == SubscriptionStatus.ACTIVE
                && currentPeriodEnd != null
                && currentPeriodEnd.isBefore(day)) {
            return SubscriptionStatus.EXPIRED;
        }
        return status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getPlanCode() {
        return planCode;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public String getNote() {
        return note;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
