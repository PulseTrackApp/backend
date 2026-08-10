package com.pulsetrack.backend.push;

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
 * Jeton d'enregistrement FCM d'un appareil.
 *
 * <p>Le jeton est unique <em>globalement</em>, pas par utilisateur : un meme
 * telephone reinstalle avec un autre compte doit changer de proprietaire, sinon
 * l'ancien compte continuerait de recevoir les notifications du nouveau.
 *
 * <p>{@code lastSeenAt} sert au menage : FCM invalide les jetons des
 * applications desinstallees, et un jeton qui n'a plus donne signe de vie depuis
 * des mois n'a plus lieu d'etre conserve.
 */
@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Requis par JPA. */
    protected DeviceToken() {
    }

    public DeviceToken(UUID userId, String token, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /**
     * Reattribue le jeton au compte courant et le rafraichit. Appele a chaque
     * ouverture de l'application, ce qui rend l'enregistrement idempotent.
     */
    public void refresh(UUID userId, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
