package com.pulsetrack.backend.coach;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Reglages de l'assistant Gemini pour un utilisateur.
 *
 * <p>La cle API est celle de l'utilisateur, pas celle du serveur : c'est son
 * quota, sa facture, sa decision. Elle est stockee <strong>chiffree</strong> et
 * n'est jamais renvoyee par l'API — le client apprend seulement qu'une cle
 * existe, via {@link #hasApiKey()}.
 *
 * <p>La cle primaire est {@code user_id} : il y a exactement un jeu de reglages
 * par compte, autant le dire au schema plutot que d'ajouter un identifiant
 * technique et un index d'unicite.
 */
@Entity
@Table(name = "gemini_settings")
public class GeminiSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "encrypted_api_key")
    private String encryptedApiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "coaching_tone", nullable = false)
    private CoachingTone coachingTone;

    @Column(name = "weekly_review_enabled", nullable = false)
    private boolean weeklyReviewEnabled;

    @Column(name = "effort_warnings_enabled", nullable = false)
    private boolean effortWarningsEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par JPA. */
    protected GeminiSettings() {
    }

    /**
     * Reglages par defaut d'un compte : assistant desactive, aucune cle. La spec
     * exige que l'application fonctionne sans Gemini ; ne rien activer tant que
     * l'utilisateur ne l'a pas demande est la traduction directe de cette regle.
     */
    public GeminiSettings(UUID userId, Instant now) {
        this.userId = userId;
        this.enabled = false;
        this.coachingTone = CoachingTone.ENCOURAGING;
        this.weeklyReviewEnabled = true;
        this.effortWarningsEnabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updatePreferences(boolean enabled,
                                  CoachingTone coachingTone,
                                  boolean weeklyReviewEnabled,
                                  boolean effortWarningsEnabled,
                                  Instant now) {
        this.enabled = enabled;
        this.coachingTone = coachingTone;
        this.weeklyReviewEnabled = weeklyReviewEnabled;
        this.effortWarningsEnabled = effortWarningsEnabled;
        this.updatedAt = now;
    }

    /**
     * @param encryptedApiKey cle deja chiffree ; cette classe ne manipule jamais
     *                        de cle en clair, ce qui evite qu'une future methode
     *                        {@code toString()} ne la fasse fuir dans les logs
     */
    public void storeApiKey(String encryptedApiKey, Instant now) {
        this.encryptedApiKey = encryptedApiKey;
        this.updatedAt = now;
    }

    /**
     * Efface la cle et coupe l'assistant : garder {@code enabled = true} sans cle
     * ne produirait que des erreurs a chaque appel.
     */
    public void clearApiKey(Instant now) {
        this.encryptedApiKey = null;
        this.enabled = false;
        this.updatedAt = now;
    }

    public boolean hasApiKey() {
        return encryptedApiKey != null && !encryptedApiKey.isBlank();
    }

    /** L'assistant n'est utilisable que s'il est actif <em>et</em> muni d'une cle. */
    public boolean isUsable() {
        return enabled && hasApiKey();
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public CoachingTone getCoachingTone() {
        return coachingTone;
    }

    public boolean isWeeklyReviewEnabled() {
        return weeklyReviewEnabled;
    }

    public boolean isEffortWarningsEnabled() {
        return effortWarningsEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
