package com.pulsetrack.backend.access;

import java.time.Instant;
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
 * Octroi d'un module a un compte. La presence de la ligne vaut autorisation,
 * son absence vaut refus.
 *
 * <p>{@code userId} en simple {@link UUID} plutot qu'une association
 * {@code @ManyToOne} vers {@code User} : ces lignes sont lues a chaque requete
 * authentifiee, et une association ferait charger l'utilisateur entier pour ne
 * consulter qu'un identifiant deja connu de l'appelant.
 */
@Entity
@Table(name = "user_modules")
public class UserModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppModule module;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /** Requis par JPA. */
    protected UserModule() {
    }

    public UserModule(UUID userId, AppModule module, Instant grantedAt) {
        this.userId = userId;
        this.module = module;
        this.grantedAt = grantedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AppModule getModule() {
        return module;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
