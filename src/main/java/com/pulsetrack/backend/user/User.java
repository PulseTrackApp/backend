package com.pulsetrack.backend.user;

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
 * Compte d'authentification. Volontairement minimal : tout ce qui releve du
 * sportif (poids, objectif, sports pratiques) vit dans
 * {@code UserProfile}, qui peut evoluer sans toucher a l'authentification.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Toujours stocke en minuscules : c'est ce qui rend l'unicite insensible a la casse. */
    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * {@link EnumType#STRING} et non {@code ORDINAL} : un ordinal fige l'ordre
     * de declaration de l'enumeration dans la base, et inserer une valeur au
     * milieu reattribuerait silencieusement les roles de tous les comptes.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    /** Requis par JPA. */
    protected User() {
    }

    public User(String email, String passwordHash, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.role = Role.USER;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Remplace le mot de passe, deja hache par l'appelant.
     *
     * <p>L'entite n'accepte jamais un mot de passe en clair : le hachage releve
     * du service, qui detient l'encodeur, et une valeur en clair arrivee
     * jusqu'ici finirait telle quelle en base.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Change le niveau de privilege.
     *
     * <p>Le service appelant reste responsable d'interdire qu'un administrateur
     * se retire son propre role : l'entite ne connait pas l'auteur du geste, et
     * ce controle-la se joue avec l'identite de l'appelant en main.
     */
    public void changeRole(Role newRole) {
        this.role = newRole;
    }
}
