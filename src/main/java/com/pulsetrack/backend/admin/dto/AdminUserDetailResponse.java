package com.pulsetrack.backend.admin.dto;

import java.time.Instant;

/**
 * Fiche d'un compte : le meme objet que dans la liste, plus de quoi juger de son
 * activite.
 *
 * <p>Composition plutot qu'heritage : les enregistrements Java ne s'etendent
 * pas, et l'imbrication garde une forme JSON previsible pour le client.
 *
 * @param workoutCount  nombre total de seances. Un simple decompte, pas leur
 *                      contenu : savoir si un compte sert encore ne demande pas
 *                      de lire ou l'utilisateur a couru
 * @param lastWorkoutAt {@code null} si le compte n'a jamais rien enregistre
 */
public record AdminUserDetailResponse(AdminUserResponse account,
                                      long workoutCount,
                                      Instant lastWorkoutAt) {
}
