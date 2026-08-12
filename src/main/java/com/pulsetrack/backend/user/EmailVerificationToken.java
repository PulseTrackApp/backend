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
 * Code de confirmation d'adresse, stocke sous forme d'empreinte.
 *
 * <p>Meme forme que {@link PasswordResetToken}, et pour les memes raisons : le
 * code en clair n'existe que dans le courriel, et la ligne survit a son usage
 * jusqu'a expiration pour qu'un second emploi soit refuse plutot que confondu
 * avec un code inconnu.
 *
 * <p>Deux tables plutot qu'une seule munie d'un discriminant : un code de
 * verification et un code de reinitialisation n'ont ni la meme duree de vie ni
 * les memes consequences, et les melanger ferait qu'une erreur de filtrage
 * transformerait une confirmation d'adresse en changement de mot de passe.
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Empreinte SHA-256 hexadecimale du code envoye par courriel. */
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
    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
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

    public void useAt(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }
}
