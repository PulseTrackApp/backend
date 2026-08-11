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
 * Code de reinitialisation envoye par courriel, stocke sous forme d'empreinte.
 *
 * <p>La ligne survit a son usage jusqu'a expiration : c'est ce qui permet de
 * refuser un second emploi du meme code, alors qu'une suppression le rendrait
 * indistinguable d'un code inconnu.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Empreinte SHA-256 hexadecimale ; le code en clair n'existe que dans le courriel. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Nul tant que le code n'a pas servi. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Requis par JPA. */
    protected PasswordResetToken() {
    }

    public PasswordResetToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean hasExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Utilisable une seule fois : rejouer le meme code ne doit rien donner. */
    public void useAt(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }
}
