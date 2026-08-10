package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Jeton de renouvellement d'une session, stocke sous forme d'empreinte.
 *
 * <p>C'est la seule partie revocable de l'authentification : le jeton d'acces
 * est un JWT autoportant, que personne ne peut rappeler une fois signe.
 * Supprimer ou revoquer la ligne correspondante ici empeche le renouvellement,
 * donc eteint la session au plus tard a l'expiration du jeton d'acces en cours.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Empreinte SHA-256 hexadecimale ; la valeur en clair n'existe que chez le client. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Nul tant que le jeton est utilisable. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Requis par JPA. */
    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean hasExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Idempotent : revoquer deux fois ne repousse pas la date, sans quoi une
     * deconnexion rejouee brouillerait la trace de la premiere.
     */
    public void revokeAt(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
