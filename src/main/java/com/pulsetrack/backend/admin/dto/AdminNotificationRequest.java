package com.pulsetrack.backend.admin.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Une notification composee depuis l'application d'administration.
 *
 * <p><strong>Le destinataire est explicite, jamais deduit.</strong> Un champ
 * {@code userId} laisse vide aurait pu vouloir dire « tout le monde », et une
 * erreur de saisie aurait alors envoye a des centaines de telephones un message
 * destine a un seul. Le champ {@code target} oblige a dire lequel des deux on
 * veut, et le serveur refuse les combinaisons incoherentes.
 *
 * @param target destinataires vises
 * @param userId compte vise ; obligatoire pour {@link Target#USER}, interdit
 *               pour {@link Target#ALL}
 * @param title  titre affiche sur le telephone
 * @param body   corps du message
 * @param route  ecran a ouvrir au clic, par exemple {@code /stats}. Facultatif :
 *               sans lui, la notification ouvre simplement l'application
 */
public record AdminNotificationRequest(
        @NotNull Target target,
        UUID userId,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 400) String body,
        @Size(max = 120) String route) {

    /** Qui recoit. Constante de protocole : l'ecran d'administration s'y adosse. */
    public enum Target {

        /** Un compte precis. */
        USER,

        /** Tous les comptes ayant au moins un appareil enregistre. */
        ALL
    }
}
