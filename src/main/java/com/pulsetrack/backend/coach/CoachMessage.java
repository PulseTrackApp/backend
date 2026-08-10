package com.pulsetrack.backend.coach;

import java.time.Instant;
import java.time.LocalDate;
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
 * Conseil produit par Gemini, conserve apres generation.
 *
 * <p>On persiste pour trois raisons : le dashboard affiche « le dernier conseil »
 * sans rappeler l'API, le quota de l'utilisateur est preserve, et l'historique
 * des conseils reste consultable meme apres suppression de la cle.
 *
 * <p>Le prompt est conserve a cote de la reponse : sans lui, un conseil surprenant
 * serait impossible a expliquer apres coup.
 */
@Entity
@Table(name = "coach_messages")
public class CoachMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private CoachMessageKind kind;

    /** Lundi de la semaine analysee ; nul pour une question libre. */
    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** Modele ayant produit la reponse, pour comparer apres une montee de version. */
    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Requis par JPA. */
    protected CoachMessage() {
    }

    public CoachMessage(UUID userId,
                        CoachMessageKind kind,
                        LocalDate weekStart,
                        String prompt,
                        String content,
                        String model,
                        Instant createdAt) {
        this.userId = userId;
        this.kind = kind;
        this.weekStart = weekStart;
        this.prompt = prompt;
        this.content = content;
        this.model = model;
        this.createdAt = createdAt;
    }

    /** Remplace le contenu lors d'une regeneration explicite du meme bilan. */
    public void replaceContent(String prompt, String content, String model, Instant now) {
        this.prompt = prompt;
        this.content = content;
        this.model = model;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public CoachMessageKind getKind() {
        return kind;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
